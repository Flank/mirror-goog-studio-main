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
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <memory>
#include <sstream>
#include <vector>

#include "utils/bash_command.h"
#include "utils/clock.h"
#include "utils/current_process.h"
#include "utils/fs/file_system.h"
#include "utils/installer.h"
#include "utils/log.h"
#include "utils/package_manager.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using std::string;

namespace profiler {

const char *SimplePerfManager::kSimpleperfExecutable = "simpleperf";

SimplePerfManager::~SimplePerfManager() {
  string error;
  for (auto const &record : profiled_) {
    StopSimplePerf(record.second, &error);
  }
}

bool SimplePerfManager::EnableProfiling(std::string *error) const {
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

bool SimplePerfManager::StartProfiling(const std::string &app_name,
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

  // Install simple_perf
  string app_pkg_name = ProcessManager::GetPackageNameFromAppName(app_name);
  Installer installer(app_pkg_name);
  if (!installer.Install(kSimpleperfExecutable, error)) return false;

  string simple_perf_binary_abspath;
  bool inst_result = installer.GetInstallationPath(
      kSimpleperfExecutable, &simple_perf_binary_abspath, error);
  if (!inst_result) return false;

  // Build entry to keep track of what is being profiled.
  OnGoingProfiling entry;
  entry.app_pkg_name = app_pkg_name;
  entry.pid = pid;

  PackageManager pm;
  if (!pm.GetAppDataPath(app_pkg_name, &entry.app_dir, error)) return false;

  string trace_filebase = GetFileBaseName(app_name);

  entry.output_prefix = trace_filebase;
  entry.trace_path =
      CurrentProcess::dir() + entry.output_prefix + ".simple_perf.trace";
  entry.log_filepath = entry.app_dir + "/" + trace_filebase + ".log";
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
      int fd = open(entry.log_filepath.c_str(), O_RDWR | O_CREAT | O_CLOEXEC,
                    S_IRUSR | S_IRGRP | S_IROTH);
      dup2(fd, fileno(stdout));
      dup2(fd, fileno(stderr));
      close(fd);

      std::stringstream string_pid;
      string_pid << pid;

      int one_sec_in_us = 1000000;
      std::stringstream samples_per_sec;
      samples_per_sec << one_sec_in_us / sampling_interval_us;

      string data_filepath = entry.app_dir + "/" + entry.output_prefix + ".dat";

      execlp("run-as", "run-as", app_pkg_name.c_str(),
             simple_perf_binary_abspath.c_str(), "record", "--call-graph",
             "dwarf", "-o", data_filepath.c_str(), "-p",
             string_pid.str().c_str(), "-f", samples_per_sec.str().c_str(),
             NULL);
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

string SimplePerfManager::GetFileBaseName(const string &app_name) const {
  std::stringstream trace_filebase;
  trace_filebase << "simpleperf-";
  trace_filebase << app_name;
  trace_filebase << "-";
  trace_filebase << clock_.GetCurrentTime();
  return trace_filebase.str();
}

bool SimplePerfManager::IsProfiling(const std::string &app_name) {
  return profiled_.find(app_name) != profiled_.end();
}

bool SimplePerfManager::StopProfiling(const std::string &app_name,
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
                 ongoing_recording.log_filepath;
    Log::D("%s", msg.c_str());
    *error = msg;
    return false;
  }

  if (!StopSimplePerf(ongoing_recording, error)) return false;

  if (!WaitForSimplerPerf(ongoing_recording, error)) return false;

  string app_pkg_name = ProcessManager::GetPackageNameFromAppName(app_name);
  if (!ConvertRawToProto(app_pkg_name, ongoing_recording, error)) return false;

  // Due to LinuxSE restrictions, "adb pull" cannot access file in app data
  // folder.
  // The workaround is to copy this file to perfd base before doing an
  // "adb pull"
  string tmp_proto_trace = ongoing_recording.app_dir + "/" +
                           ongoing_recording.output_prefix + ".tmp";
  if (!MoveTraceToPickupDir(ongoing_recording, tmp_proto_trace, app_pkg_name,
                            error)) {
    CleanUp(app_pkg_name, ongoing_recording, tmp_proto_trace);
    // Also delete dst trace since it may have been created.
    BashCommandRunner deleter("rm");
    deleter.Run(ongoing_recording.trace_path, nullptr);
    return false;
  }

  CleanUp(app_pkg_name, ongoing_recording, tmp_proto_trace);

  return true;
}

bool SimplePerfManager::StopSimplePerf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Ask simple perf to stop profiling this app.
  // It is not possible to use a system call:
  // kill(ongoing_recording.simpleperf_pid, SIGTERM);
  // because simplerpef is running under app userid.
  // We have to do it with run-as.
  Log::D("Sending SIGTERM to simpleperf(%d).",
         ongoing_recording.simpleperf_pid);
  BashCommandRunner kill_simple_perf("kill");
  std::stringstream string_pid;
  string_pid << ongoing_recording.simpleperf_pid;
  bool kill_simpler_perf_result = kill_simple_perf.RunAs(
      string_pid.str(), ongoing_recording.app_pkg_name, error);
  if (!kill_simpler_perf_result) {
    string msg = "Failed to send SIGTERM to simpleperf";
    error->append("\n");
    error->append(msg);
    Log::D("%s", msg.c_str());
    return false;
  }
  return true;
}

bool SimplePerfManager::MoveTraceToPickupDir(
    const OnGoingProfiling &ongoing_recording, const string &tmp_proto_trace,
    const string &app_pkg_name, string *error) const {
  string output;

  // Make sure dst file does not exist.
  BashCommandRunner deleter("rm");
  deleter.Run(ongoing_recording.trace_path, nullptr);

  std::stringstream copy_command;
  copy_command << "-c 'run-as ";
  copy_command << app_pkg_name << " ";
  copy_command << "cat ";
  copy_command << tmp_proto_trace << "'";
  copy_command << " | ";
  copy_command << "cat > ";
  copy_command << ongoing_recording.trace_path;

  BashCommandRunner runner("sh");
  bool copyResult = runner.Run(copy_command.str(), &output);
  if (!copyResult) {
    string msg = "Unable to copy report to pickup area:" + output;
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}

void SimplePerfManager::CleanUp(const string &app_pkg_name,
                                const OnGoingProfiling &ongoing_recording,
                                const string &tmp_proto_trace) const {
  BashCommandRunner deleter("rm");
  deleter.RunAs(tmp_proto_trace, app_pkg_name, nullptr);
  deleter.RunAs(ongoing_recording.log_filepath, app_pkg_name, nullptr);
}

bool SimplePerfManager::ConvertRawToProto(
    const string &app_pkg_name, const OnGoingProfiling &ongoing_recording,
    string *error) const {
  string simple_perf_binary_abspath;
  Installer installer(app_pkg_name);
  installer.GetInstallationPath(kSimpleperfExecutable,
                                &simple_perf_binary_abspath, error);
  BashCommandRunner simpleperf_report(simple_perf_binary_abspath);
  string output;
  std::stringstream parameters;
  parameters << "report-sample ";
  parameters << "--protobuf ";
  parameters << "--show-callchain ";
  parameters << "-i ";
  parameters << ongoing_recording.app_dir + "/" +
                    ongoing_recording.output_prefix + ".dat";
  parameters << " ";
  parameters << "-o ";
  parameters << ongoing_recording.app_dir + "/" +
                    ongoing_recording.output_prefix + ".tmp";
  bool report_result =
      simpleperf_report.RunAs(parameters.str(), app_pkg_name, &output);
  if (!report_result) {
    string msg = "Unable to generate simpleperf report:" + output;
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}

bool SimplePerfManager::WaitForSimplerPerf(
    const OnGoingProfiling &ongoing_recording, string *error) const {
  // Wait until simpleperf is done outputting collected data to the .dat
  // file.
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
                 ongoing_recording.log_filepath;
    Log::D("%s", msg.c_str());
    error->append("\n");
    error->append(msg);
    return false;
  }
  return true;
}
}  // namespace profiler
