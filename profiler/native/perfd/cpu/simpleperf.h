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
 */

#ifndef CPU_SIMPLEPERF_H_
#define CPU_SIMPLEPERF_H_

#include <string>

namespace profiler {

// Service to manage interactions related with simpleperf profiling tool
// (e.g. invoking simpleperf commands, enabling simpleperf on the device, etc.).
// Designed to be easily inherited and used in tests.
class Simpleperf {
 public:
  ~Simpleperf() = default;

  // Make sure profiling is enabled on the platform (otherwise LinuxSE prevents
  // it). Returns true on success.
  virtual bool EnableProfiling() const;

  // Kill simpleperf and returns true if it was killed successfully.
  virtual bool KillSimpleperf(int simpleperf_pid) const;

  // Invoke `simpleperf record` given the directory containing the simpleperf
  // binary, the |pid| of the process to be profiled, its corresponding package
  // name, the path of the resulting trace file, and the sampling interval. Also
  // redirects stdout and stderr to a log file located at |log_path|.
  virtual void Record(const std::string& simpleperf_dir, int pid,
                      const std::string& pkg_name,
                      const std::string& trace_path, int sampling_interval_us,
                      const std::string& log_path) const;

  // Receives the directory containing the simpleperf binary and invokes
  // `simpleperf report-sample` passing |input_path| as input file and
  // |output_path| as the protobuf output file. Adds the command output to
  // |output| and return trues on success.
  virtual bool ReportSample(const std::string& simpleperf_dir,
                            const std::string& input_path,
                            const std::string& output_path,
                            std::string* output) const;

 private:
  static const char* kSimpleperfExecutable;
};
}

#endif  // CPU_SIMPLEPERF_H_
