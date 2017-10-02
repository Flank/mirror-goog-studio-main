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
#include <sstream>
#include <string>

#include "utils/bash_command.h"

using std::string;
using std::stringstream;

namespace profiler {

const char* Simpleperf::kSimpleperfExecutable = "simpleperf";

bool Simpleperf::EnableProfiling() const {
  // By default, linuxSE disallow profiling. This enables it.
  // simpleperf already has CTS tests ensuring the following command running
  // successfully.
  BashCommandRunner enable_profiling("setprop");
  return enable_profiling.Run("security.perf_harden 0", nullptr);
}

bool Simpleperf::KillSimpleperf(int simpleperf_pid) const {
  BashCommandRunner kill_simpleperf("kill");
  stringstream string_pid;
  string_pid << simpleperf_pid;
  return kill_simpleperf.Run(string_pid.str(), nullptr);
}

void Simpleperf::Record(const string& simpleperf_dir, int pid,
                        const string& pkg_name, const string& trace_path,
                        int sampling_interval_us,
                        const string& log_path) const {
  //  Redirect stdout and stderr to a log file (useful if simpleperf
  //  crashes).
  int fd = open(log_path.c_str(), O_RDWR | O_CREAT | O_CLOEXEC,
                S_IRUSR | S_IRGRP | S_IROTH);
  dup2(fd, fileno(stdout));
  dup2(fd, fileno(stderr));
  close(fd);

  std::stringstream string_pid;
  string_pid << pid;

  string simpleperf_bin = simpleperf_dir + kSimpleperfExecutable;

  int one_sec_in_us = 1000000;
  std::stringstream samples_per_sec;
  samples_per_sec << one_sec_in_us / sampling_interval_us;

  execlp(simpleperf_bin.c_str(), simpleperf_bin.c_str(), "record", "--app",
         pkg_name.c_str(), "--call-graph", "dwarf", "-o", trace_path.c_str(),
         "-p", string_pid.str().c_str(), "-f", samples_per_sec.str().c_str(),
         "--exit-with-parent", NULL);
}

bool Simpleperf::ReportSample(const string& simpleperf_dir,
                              const string& input_path,
                              const string& output_path, string* output) const {
  string simpleperf_binary_abspath = simpleperf_dir + kSimpleperfExecutable;
  BashCommandRunner simpleperf_report(simpleperf_binary_abspath);
  stringstream parameters;
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

}  // namespace profiler
