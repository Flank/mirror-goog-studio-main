/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "simpleperf_manager.h"

#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <sstream>

#include "utils/bash_command.h"
#include "utils/current_process.h"
#include "utils/fs/disk_file_system.h"
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using profiler::proto::TraceStopStatus;
using std::string;

namespace profiler {

SimpleperfManager::~SimpleperfManager() {
  // This is not necessary thanks to TerminationService. But keep it to be safe.
  Shutdown();
}

void SimpleperfManager::Shutdown() {
  // Intentionally not protected by |start_stop_mutex_| so this function can
  // proceed without being blocked.
  string error;
  for (auto const &record : profiled_) {
    const OnGoingProfiling &ongoing = record.second;
    StopSimpleperf(ongoing, &error);
  }
}

bool SimpleperfManager::StartProfiling(const std::string &app_name,
                                       const std::string &abi_arch,
                                       int sampling_interval_us,
                                       const std::string &trace_path,
                                       std::string *error,
                                       bool is_startup_profiling) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU: StartProfiling simplerperf");
  Log::D(Log::Tag::PROFILER, "Profiler:Received query to profile %s",
         app_name.c_str());

  if (IsProfiling(app_name)) {
    error->append("Simpleperf is already running; start tracing failed.");
    return false;
  }

  int pid = kStartupProfilingPid;
  if (!is_startup_profiling) {
    ProcessManager process_manager;
    pid = process_manager.GetPidForBinary(app_name);
    if (pid < 0) {
      error->append("\n");
      error->append("Unable to get process id to profile.");
      return false;
    }
    Log::D(Log::Tag::PROFILER, "%s app has pid:%d", app_name.c_str(), pid);
  }

  if (!simpleperf_->EnableProfiling()) {
    error->append("\n");
    error->append("Unable to setprop to enable profiling.");
    return false;
  }
  // Build entry to keep track of what is being profiled.
  OnGoingProfiling entry;
  entry.pid = pid;
  entry.process_name = ProcessManager::GetPackageNameFromAppName(app_name);
  entry.abi_arch = abi_arch;
  entry.trace_path = trace_path;
  std::ostringstream temp_file_name;
  temp_file_name << "simpleperf-" << app_name;
  entry.log_file_path = CurrentProcess::dir() + temp_file_name.str() + ".log";
  entry.raw_trace_path = CurrentProcess::dir() + temp_file_name.str() + ".dat";

  // fork process to run simpleperf profiling.
  int forkpid = fork();
  switch (forkpid) {
    case -1: {
      error->append("\n");
      error->append("Unable to create(fork) simpleperf process");
      return false;
      break;  // Useless but make the compiler happy.
    }
    case 0: {  // Child Process
      simpleperf_->Record(pid, entry.process_name, abi_arch,
                          entry.raw_trace_path, sampling_interval_us,
                          entry.log_file_path);
      exit(EXIT_FAILURE);
      break;  // Useless break but makes compiler happy.
    }
    default: {  // Perfd Process
      entry.simpleperf_pid = forkpid;
      profiled_[app_name] = entry;
      Log::D(Log::Tag::PROFILER, "Registered app %s profiled by %d",
             app_name.c_str(), entry.simpleperf_pid);
    }
  }
  return true;
}

bool SimpleperfManager::IsProfiling(const std::string &app_name) {
  return profiled_.find(app_name) != profiled_.end();
}

TraceStopStatus::Status SimpleperfManager::StopProfiling(
    const std::string &app_name, bool need_result, std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU:StopProfiling simpleperf");
  Log::D(Log::Tag::PROFILER, "Profiler:Stopping profiling for %s",
         app_name.c_str());
  if (!IsProfiling(app_name)) {
    string msg = "This app was not being profiled.";
    Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return TraceStopStatus::NO_ONGOING_PROFILING;
  }

  OnGoingProfiling ongoing_recording;
  ongoing_recording = profiled_[app_name];
  profiled_.erase(app_name);

  ProcessManager pm;
  pid_t current_pid = pm.GetPidForBinary(app_name);
  Log::D(Log::Tag::PROFILER, "%s app has pid:%d", app_name.c_str(),
         current_pid);

  TraceStopStatus::Status status = TraceStopStatus::SUCCESS;
  if (need_result) {
    // Make sure it is still running.
    if (current_pid == -1) {
      string msg = "App died since profiling started.";
      error->append("\n");
      error->append(msg);
      Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
      status = TraceStopStatus::APP_PROCESS_DIED;
    }

    // Make sure pid is what is expected. A startup profiling didn't have pid
    // available when it started, so it is an exception.
    if (ongoing_recording.pid != kStartupProfilingPid &&
        ongoing_recording.pid != current_pid) {
      // Looks like the app was restarted. Simpleperf died as a result.
      string msg = "Recorded pid and current app pid do not match: Aborting";
      error->append("\n");
      error->append(msg);
      Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
      status = TraceStopStatus::APP_PID_CHANGED;
    }
  }

  // No simpleperf should be running after tracing is stopped. Simpleperf is
  // expected to die when the app exits, but there may be bug preventing it
  // killing itself. Simpleperf may also die (due to bugs) even if the app is
  // running.
  if (!pm.IsPidAlive(ongoing_recording.simpleperf_pid)) {
    string msg = "Simpleperf died while profiling. Logfile :" +
                 ongoing_recording.log_file_path;
    Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
    *error = msg;
    status = TraceStopStatus::PROFILER_PROCESS_DIED;
  } else {
    bool stop_simpleperf_success = StopSimpleperf(ongoing_recording, error);
    if (!stop_simpleperf_success) {
      status = TraceStopStatus::STOP_COMMAND_FAILED;
    } else {
      if (!WaitForSimpleperf(ongoing_recording, error)) {
        status = TraceStopStatus::WAIT_FAILED;
      }
    }
  }

  if (need_result && status == TraceStopStatus::SUCCESS) {
    // Copy the raw trace to the path returned by CPU service.
    if (!CopyRawToTrace(ongoing_recording, error)) {
      status = TraceStopStatus::CANNOT_COPY_FILE;
    }
  }

  CleanUp(ongoing_recording);

  return status;
}

bool SimpleperfManager::StopSimpleperf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Ask simpleperf to stop profiling this app.
  Log::D(Log::Tag::PROFILER, "Sending SIGTERM to simpleperf(%d).",
         ongoing_recording.simpleperf_pid);
  bool kill_simpleperf_result = simpleperf_->KillSimpleperf(
      ongoing_recording.simpleperf_pid, ongoing_recording.process_name);

  if (!kill_simpleperf_result) {
    string msg = "Failed to send SIGTERM to simpleperf";
    error->append("\n");
    error->append(msg);
    Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
    return false;
  }
  return true;
}

void SimpleperfManager::CleanUp(
    const OnGoingProfiling &ongoing_recording) const {
  BashCommandRunner deleter("rm -f");
  deleter.Run(ongoing_recording.raw_trace_path, nullptr);
  // Don't delete |ongoing_recording.log_file_path| because the log is useful in
  // debugging. Each app has at most one log file and the size of the log
  // file should be manageable.
}

bool SimpleperfManager::CopyRawToTrace(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  bool move_trace_success = file_system_->MoveFile(
      ongoing_recording.raw_trace_path, ongoing_recording.trace_path);
  if (!move_trace_success) {
    string msg = "Unable to copy simpleperf raw trace.";
    Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}

bool SimpleperfManager::WaitForSimpleperf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Wait until simpleperf is done outputting collected data to the .dat file.
  int status = 0;
  int wait_result =
      simpleperf_->WaitForSimpleperf(ongoing_recording.simpleperf_pid, &status);

  if (wait_result == -1) {
    string msg = "waitpid failed with message: ";
    msg += strerror(errno);
    Log::D(Log::Tag::PROFILER, "%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }

  // Make sure simpleperf exited normally.
  if (!(WIFEXITED(status) && WEXITSTATUS(status) == 0)) {
    std::ostringstream msg;
    msg << "Simpleperf did not exit as expected. Logfile: ";
    msg << ongoing_recording.log_file_path << ". ";
    msg << "Exit status: " << status;
    Log::D(Log::Tag::PROFILER, "%s", msg.str().c_str());
    error->append("\n");
    error->append(msg.str());
    return false;
  }
  return true;
}
}  // namespace profiler
