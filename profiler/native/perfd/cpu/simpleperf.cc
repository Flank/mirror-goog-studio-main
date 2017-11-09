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

using std::strcmp;
using std::strcpy;
using std::string;
using std::ostringstream;

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

bool Simpleperf::KillSimpleperf(int simpleperf_pid) const {
  BashCommandRunner kill_simpleperf("kill");
  ostringstream string_pid;
  string_pid << simpleperf_pid;
  return kill_simpleperf.Run(string_pid.str(), nullptr);
}

void Simpleperf::Record(int pid, const string& pkg_name,
                        const string& trace_path, int sampling_interval_us,
                        const string& log_path) const {
  //  Redirect stdout and stderr to a log file (useful if simpleperf
  //  crashes).
  int fd = open(log_path.c_str(), O_RDWR | O_CREAT | O_CLOEXEC,
                S_IRUSR | S_IRGRP | S_IROTH);
  dup2(fd, fileno(stdout));
  dup2(fd, fileno(stderr));
  close(fd);

  string record_command =
      GetRecordCommand(pid, pkg_name, trace_path, sampling_interval_us);

  // Converts the record command to a C string.
  char command_buffer[record_command.length() + 1];
  strcpy(command_buffer, record_command.c_str());
  char* argv[record_command.length() + 1];
  SplitRecordCommand(command_buffer, argv);

  // Execute the simpleperf record command.
  execvp(*argv, argv);
}

bool Simpleperf::ReportSample(const string& input_path,
                              const string& output_path, string* output) const {
  string simpleperf_binary_abspath = simpleperf_dir_ + kSimpleperfExecutable;
  BashCommandRunner simpleperf_report(simpleperf_binary_abspath);
  ostringstream parameters;
  parameters << "report-sample ";
  parameters << "--protobuf ";
  parameters << "--show-callchain ";
  parameters << "-i ";
  parameters << input_path;
  parameters << " ";
  parameters << "-o ";
  parameters << output_path;

  return simpleperf_report.Run(parameters.str(), output);
}

string Simpleperf::GetRecordCommand(int pid, const string& pkg_name,
                                    const string& trace_path,
                                    int sampling_interval_us) const {
  ostringstream command;
  command << simpleperf_dir_ << kSimpleperfExecutable << " record";
  command << " -p " << pid;
  command << " --app " << pkg_name;

  string supported_features = GetFeatures();
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
    command << "-e cpu-clock";
  }

  command << " --exit-with-parent";

  return command.str();
}

string Simpleperf::GetFeatures() const {
  string simpleperf_bin = simpleperf_dir_ + kSimpleperfExecutable;
  BashCommandRunner list_features(simpleperf_bin);
  string supported_features;
  list_features.Run("list --show-features", &supported_features);
  return supported_features;
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
