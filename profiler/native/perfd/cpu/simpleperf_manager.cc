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
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using std::string;

namespace profiler {

SimpleperfManager::~SimpleperfManager() {
  string error;
  for (auto const &record : profiled_) {
    StopSimpleperf(record.second, &error);
  }
}

bool SimpleperfManager::StartProfiling(const std::string &app_name,
                                       const std::string &abi_arch,
                                       int sampling_interval_us,
                                       std::string *trace_path,
                                       std::string *error,
                                       bool is_startup_profiling) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU: StartProfiling simplerperf");
  Log::D("Profiler:Received query to profile %s", app_name.c_str());

  if (IsProfiling(app_name)) {
    OnGoingProfiling ongoing_recording = profiled_[app_name];
    *trace_path = ongoing_recording.trace_path;
    return true;
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
    Log::D("%s app has pid:%d", app_name.c_str(), pid);
  }

  if (!simpleperf_.EnableProfiling()) {
    error->append("\n");
    error->append("Unable to setprop to enable profiling.");
    return false;
  }
  // Build entry to keep track of what is being profiled.
  OnGoingProfiling entry;
  entry.pid = pid;
  entry.abi_arch = abi_arch;
  entry.output_prefix = GetFileBaseName(app_name);
  entry.trace_path =
      CurrentProcess::dir() + entry.output_prefix + ".simpleperf.trace";
  entry.log_file_path = CurrentProcess::dir() + entry.output_prefix + ".log";
  entry.raw_trace_path = CurrentProcess::dir() + entry.output_prefix + ".dat";
  // Point trace path to entry's trace path so the trace can be pulled later.
  *trace_path = entry.trace_path;

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
      simpleperf_.Record(
          pid, ProcessManager::GetPackageNameFromAppName(app_name), abi_arch,
          entry.raw_trace_path, sampling_interval_us, entry.log_file_path);
      exit(EXIT_FAILURE);
      break;  // Useless break but makes compiler happy.
    }
    default: {  // Perfd Process
      entry.simpleperf_pid = forkpid;
      profiled_[app_name] = entry;
      Log::D("Registered app %s profiled by %d", app_name.c_str(),
             entry.simpleperf_pid);
    }
  }
  return true;
}

string SimpleperfManager::GetFileBaseName(const string &app_name) const {
  std::ostringstream trace_filebase;
  trace_filebase << "simpleperf-";
  trace_filebase << app_name;
  trace_filebase << "-";
  trace_filebase << clock_.GetCurrentTime();
  return trace_filebase.str();
}

bool SimpleperfManager::IsProfiling(const std::string &app_name) {
  return profiled_.find(app_name) != profiled_.end();
}

bool SimpleperfManager::StopProfiling(const std::string &app_name,
                                      bool need_result, std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU:StopProfiling simpleperf");
  Log::D("Profiler:Stopping profiling for %s", app_name.c_str());
  if (!IsProfiling(app_name)) {
    string msg = "This app was not being profiled.";
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }

  OnGoingProfiling ongoing_recording;
  ongoing_recording = profiled_[app_name];
  profiled_.erase(app_name);

  ProcessManager pm;
  pid_t current_pid = pm.GetPidForBinary(app_name);
  Log::D("%s app has pid:%d", app_name.c_str(), current_pid);

  bool success = true;
  if (need_result) {
    // Make sure it is still running.
    if (current_pid == -1) {
      string msg = "App died since profiling started.";
      error->append("\n");
      error->append(msg);
      Log::D("%s", msg.c_str());
      success = false;
    }

    // Make sure pid is what is expected. A startup profiling didn't have pid
    // available when it started, so it is an exception.
    if (ongoing_recording.pid != kStartupProfilingPid &&
        ongoing_recording.pid != current_pid) {
      // Looks like the app was restarted. Simpleperf died as a result.
      string msg = "Recorded pid and current app pid do not match: Aborting";
      error->append("\n");
      error->append(msg);
      Log::D("%s", msg.c_str());
      success = false;
    }
  }

  // No simpleperf should be running after tracing is stopped. Simpleperf is
  // expected to die when the app exits, but there may be bug preventing it
  // killing itself. Simpleperf may also die (due to bugs) even if the app is
  // running.
  if (!pm.IsPidAlive(ongoing_recording.simpleperf_pid)) {
    string msg = "Simpleperf died while profiling. Logfile :" +
                 ongoing_recording.log_file_path;
    Log::D("%s", msg.c_str());
    *error = msg;
    success = false;
  } else {
    bool stop_simpleperf_success = StopSimpleperf(ongoing_recording, error);
    success = success && stop_simpleperf_success;
    if (stop_simpleperf_success) {
      if (!WaitForSimpleperf(ongoing_recording, error)) success = false;
    }
  }

  if (need_result && success) {
    if (!ConvertRawToProto(ongoing_recording, error)) success = false;
  }

  CleanUp(ongoing_recording);

  return success;
}

bool SimpleperfManager::StopSimpleperf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Ask simpleperf to stop profiling this app.
  Log::D("Sending SIGTERM to simpleperf(%d).",
         ongoing_recording.simpleperf_pid);
  bool kill_simpleperf_result =
      simpleperf_.KillSimpleperf(ongoing_recording.simpleperf_pid);

  if (!kill_simpleperf_result) {
    string msg = "Failed to send SIGTERM to simpleperf";
    error->append("\n");
    error->append(msg);
    Log::D("%s", msg.c_str());
    return false;
  }
  return true;
}

void SimpleperfManager::CleanUp(
    const OnGoingProfiling &ongoing_recording) const {
  BashCommandRunner deleter("rm -f");
  deleter.Run(ongoing_recording.raw_trace_path, nullptr);
  deleter.Run(ongoing_recording.log_file_path, nullptr);
}

bool SimpleperfManager::ConvertRawToProto(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  string output;
  bool report_sample_result = simpleperf_.ReportSample(
      ongoing_recording.raw_trace_path, ongoing_recording.trace_path,
      ongoing_recording.abi_arch, &output);
  if (!report_sample_result) {
    string msg = "Unable to generate simpleperf report:" + output;
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}

bool SimpleperfManager::WaitForSimpleperf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Wait until simpleperf is done outputting collected data to the .dat file.
  int status;
  int wait_result = waitpid(ongoing_recording.simpleperf_pid, &status, 0);

  if (wait_result == -1) {
    string msg = "waitpid failed with message: ";
    msg += strerror(errno);
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }

  // Make sure simpleperf exited normally.
  if (!WIFEXITED(status)) {
    string msg = "Simpleperf did not exit as expected. Logfile: " +
                 ongoing_recording.log_file_path;
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}
}  // namespace profiler
