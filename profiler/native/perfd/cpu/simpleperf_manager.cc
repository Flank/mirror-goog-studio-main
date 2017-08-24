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

#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
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

const char *SimpleperfManager::kSimpleperfExecutable = "simpleperf";

SimpleperfManager::~SimpleperfManager() {
  string error;
  for (auto const &record : profiled_) {
    StopSimpleperf(record.second, &error);
  }
}

bool SimpleperfManager::EnableProfiling(std::string *error) const {
  // By default, linuxSE disallow profiling. This enables it.
  // simpleperf already has CTS tests ensuring the following command running
  // successfully.
  string enable_profiling_output;
  BashCommandRunner enable_profiling("setprop");
  bool enable_profiling_result =
      enable_profiling.Run("security.perf_harden 0", &enable_profiling_output);
  if (!enable_profiling_result) {
    error->append("\n");
    error->append("Unable to setprop to enable profiling.");
    return false;
  }
  return true;
}

bool SimpleperfManager::StartProfiling(const std::string &app_name,
                                       int sampling_interval_us,
                                       std::string *trace_path,
                                       std::string *error) {
  std::lock_guard<std::mutex> lock(start_stop_mutex_);
  Trace trace("CPU: StartProfiling simplerperf");
  Log::D("Profiler:Received query to profile %s", app_name.c_str());

  if (IsProfiling(app_name)) {
    OnGoingProfiling ongoing_recording = profiled_[app_name];
    *trace_path = ongoing_recording.trace_path;
    return true;
  }

  ProcessManager process_manager;
  int pid = process_manager.GetPidForBinary(app_name);
  if (pid < 0) {
    error->append("\n");
    error->append("Unable to get process id to profile.");
    return false;
  }
  Log::D("%s app has pid:%d", app_name.c_str(), pid);

  if (!EnableProfiling(error)) return false;

  // Build entry to keep track of what is being profiled.
  OnGoingProfiling entry;
  entry.pid = pid;
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
      //  Redirect stdout and stderr to a file (useful is perf crashes).
      int fd = open(entry.log_file_path.c_str(), O_RDWR | O_CREAT | O_CLOEXEC,
                    S_IRUSR | S_IRGRP | S_IROTH);
      dup2(fd, fileno(stdout));
      dup2(fd, fileno(stderr));
      close(fd);

      std::stringstream string_pid;
      string_pid << pid;

      int one_sec_in_us = 1000000;
      std::stringstream samples_per_sec;
      samples_per_sec << one_sec_in_us / sampling_interval_us;

      const char *app_pkg_name =
          ProcessManager::GetPackageNameFromAppName(app_name).c_str();
      const char *simpleperf_bin =
          (CurrentProcess::dir() + kSimpleperfExecutable).c_str();
      execlp(simpleperf_bin, simpleperf_bin, "record", "--app", app_pkg_name,
             "--call-graph", "dwarf", "-o", entry.raw_trace_path.c_str(), "-p",
             string_pid.str().c_str(), "-f", samples_per_sec.str().c_str(),
             "--exit-with-parent", NULL);
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
  std::stringstream trace_filebase;
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
                                      std::string *error) {
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

  // Make sure it is still running.
  if (current_pid == -1) {
    string msg = "App died since profiling started.";
    error->append("\n");
    error->append(msg);
    Log::D("%s", msg.c_str());
    return false;
  }

  // Make sure pid is what is expected
  if (current_pid != ongoing_recording.pid) {
    // Looks like the app was restarted. Simple perf died as a result.
    string msg = "Recorded pid and current app pid do not match: Aborting";
    error->append("\n");
    error->append(msg);
    Log::D("%s", msg.c_str());
    return false;
  }

  // Make sure simpleperf is still running.
  if (!pm.IsPidAlive(ongoing_recording.simpleperf_pid)) {
    string msg = "Simple perf died while profiling. Logfile :" +
                 ongoing_recording.log_file_path;
    Log::D("%s", msg.c_str());
    *error = msg;
    return false;
  }

  if (!StopSimpleperf(ongoing_recording, error)) return false;

  if (!WaitForSimpleperf(ongoing_recording, error)) return false;

  if (!ConvertRawToProto(ongoing_recording, error)) return false;

  CleanUp(ongoing_recording);

  return true;
}

bool SimpleperfManager::StopSimpleperf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Ask simple perf to stop profiling this app.
  Log::D("Sending SIGTERM to simpleperf(%d).",
         ongoing_recording.simpleperf_pid);
  BashCommandRunner kill_simpleperf("kill");
  std::stringstream string_pid;
  string_pid << ongoing_recording.simpleperf_pid;
  bool kill_simpleperf_result = kill_simpleperf.Run(string_pid.str(), error);
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
  string simpleperf_binary_abspath =
      CurrentProcess::dir() + kSimpleperfExecutable;
  BashCommandRunner simpleperf_report(simpleperf_binary_abspath);
  std::stringstream parameters;
  parameters << "report-sample ";
  parameters << "--protobuf ";
  parameters << "--show-callchain ";
  parameters << "-i ";
  parameters << ongoing_recording.raw_trace_path;
  parameters << " ";
  parameters << "-o ";
  parameters << ongoing_recording.trace_path;

  string output;
  bool report_result = simpleperf_report.Run(parameters.str(), &output);
  if (!report_result) {
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
    string msg = "Simpleperf did not exist as expected. Logfile:" +
                 ongoing_recording.log_file_path;
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}
}  // namespace profiler
