/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "simpleperf.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <cstring>
#include <sstream>
#include <string>

#include "utils/bash_command.h"
#include "utils/clock.h"
#include "utils/log.h"

using std::ostringstream;
using std::strcmp;
using std::strcpy;
using std::string;

namespace {

const char* const kSimpleperfExecutable = "simpleperf";

}  // namespace

namespace profiler {

bool Simpleperf::EnableProfiling() const {
  // By default, linuxSE disallow profiling. This enables it.
  // simpleperf already has CTS tests ensuring the following command running
  // successfully.
  BashCommandRunner enable_profiling("setprop");
  return enable_profiling.Run("security.perf_harden 0", nullptr);
}

bool Simpleperf::KillSimpleperf(int simpleperf_pid, const string& pkg_name) {
  string kill_cmd;
  if (is_user_build_) {
    kill_cmd = "kill";
  } else {
    // In userdebug and eng devices, kill simpleperf as root because it might
    // have been started as root.
    kill_cmd = "su root kill";
  }
  ostringstream string_pid;
  string_pid << simpleperf_pid;
  BashCommandRunner kill_simpleperf(kill_cmd, true);
  return kill_simpleperf.Run(string_pid.str(), nullptr);
}

void Simpleperf::Record(int pid, const string& pkg_name, const string& abi_arch,
                        const string& trace_path, int sampling_interval_us,
                        const string& log_path) const {
  //  Redirect stdout and stderr to a log file (useful if simpleperf
  //  crashes).
  int fd = open(log_path.c_str(), O_RDWR | O_CREAT | O_CLOEXEC,
                S_IRUSR | S_IRGRP | S_IROTH);
  dup2(fd, fileno(stdout));
  dup2(fd, fileno(stderr));
  close(fd);

  string record_command = GetRecordCommand(pid, pkg_name, abi_arch, trace_path,
                                           sampling_interval_us);

  // Converts the record command to a C string.
  char command_buffer[record_command.length() + 1];
  strcpy(command_buffer, record_command.c_str());
  char* argv[record_command.length() + 1];
  SplitRecordCommand(command_buffer, argv);

  // Execute the simpleperf record command.
  Log::D("Running Simpleperf: '%s'", record_command.c_str());
  execvp(*argv, argv);
}

bool Simpleperf::ReportSample(const string& input_path,
                              const string& output_path, const string& abi_arch,
                              string* output) {
  string simpleperf_binary_abspath = GetSimpleperfPath(abi_arch);
  BashCommandRunner simpleperf_report(simpleperf_binary_abspath, true);
  ostringstream parameters;
  parameters << "report-sample ";
  parameters << "--protobuf ";
  parameters << "--show-callchain ";
  parameters << "-i ";
  parameters << input_path;
  parameters << " ";
  parameters << "-o ";
  parameters << output_path;
  Log::D("Simpleperf report-sample command: %s %s",
         simpleperf_binary_abspath.c_str(), parameters.str().c_str());

  return simpleperf_report.Run(parameters.str(), output);
}

string Simpleperf::GetRecordCommand(int pid, const string& pkg_name,
                                    const string& abi_arch,
                                    const string& trace_path,
                                    int sampling_interval_us) const {
  ostringstream command;
  bool is_startup_profiling = pid == kStartupProfilingPid;
  if (!is_user_build_ && !is_startup_profiling) {
    // In userdebug/eng builds, we want to be able to profile processes that
    // don't have a corresponding package name (e.g. system_server) and also
    // non-debuggable apps. Running simpleperf as a normal user passing the
    // --app flag wouldn't work for these scenarios because it invokes
    // simpleperf using "run-as", and that only works with processes that
    // represent a debuggable app. A workaround for that is to invoke simpleperf
    // as root except for startup profiling, which is not a problem as startup
    // profiling is only used with debuggable apps.
    command << "su root ";
  }

  command << GetSimpleperfPath(abi_arch);
  command << " record";

  // When profiling an application startup, simpleperf profiling starts before
  // application launch, i.e when pid is not available. In this case, it will
  // rely on "--app" flag instead of "-p".
  if (pid != kStartupProfilingPid) {
    command << " -p " << pid;
  }

  // Don't add --app when profiling userdebug/eng devices unless when we're
  // using startup profiling, because in this case we don't want simpleperf to
  // be invoked using "run-as".
  if (is_user_build_ || is_startup_profiling) {
    command << " --app " << pkg_name;
  }

  string supported_features = GetFeatures(abi_arch);
  // If the device supports dwarf-based call graphs, use them. Otherwise use
  // frame pointer.
  command << " --call-graph ";
  if (supported_features.find("dwarf") != string::npos) {
    command << "dwarf";
  } else {
    command << "fp";
  }

  // If the device supports tracing offcpu time, we need to pass the
  // corresponding flag.
  if (supported_features.find("trace-offcpu") != string::npos) {
    command << " --trace-offcpu";
  }

  command << " -o " << trace_path;

  command << " -f " << (Clock::s_to_us(1) / sampling_interval_us);

  // If the device is an emulator, it doesn't support cpu-cycles events, which
  // are the default events used by simpleperf. In that case, we need to use
  // cpu-clock events.
  if (is_emulator_) {
    command << " -e cpu-clock";
  }

  command << " --exit-with-parent";

  return command.str();
}

string Simpleperf::GetFeatures(const string& abi_arch) const {
  BashCommandRunner list_features(GetSimpleperfPath(abi_arch));
  string supported_features;
  list_features.Run("list --show-features", &supported_features);
  return supported_features;
}

string Simpleperf::GetSimpleperfPath(const string& abi_arch) const {
  ostringstream path;
  path << simpleperf_dir_;
  path << kSimpleperfExecutable << "_" << abi_arch;
  return path.str();
}

void Simpleperf::SplitRecordCommand(char* original_cmd,
                                    char** split_cmd) const {
  while (*original_cmd != '\0') {
    // Replace whitespaces with \0
    while (*original_cmd == ' ') {
      *original_cmd++ = '\0';
    }
    // Add the argument to the split command array if it's not empty
    if (strcmp(original_cmd, "") != 0) {
      *split_cmd++ = original_cmd;
    }
    // Skip regular characters. They will be added to the split command once we
    // find a space or a \0.
    while (*original_cmd != '\0' && *original_cmd != ' ') {
      original_cmd++;
    }
  }
  // Mark the end of the command
  *split_cmd = nullptr;
}

}  // namespace profiler
