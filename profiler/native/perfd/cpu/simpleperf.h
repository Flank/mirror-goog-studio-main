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

#ifndef PERFD_CPU_SIMPLEPERF_H_
#define PERFD_CPU_SIMPLEPERF_H_

#include <string>

#include "utils/bash_command.h"
#include "utils/current_process.h"
#include "utils/device_info.h"

namespace profiler {

// The constant is used when profiling startup of an application with
// simpleperf, in which case simpleperf profiling starts before application
// launch, so pid is not available. The value is not -1 because system libraries
// may use it as pid is not available value. (e.g
// |ProcessManager::GetPidForBinary|).
const int kStartupProfilingPid = -12345;

// Service to manage interactions related with simpleperf profiling tool
// (e.g. invoking simpleperf commands, enabling simpleperf on the device, etc.).
// Designed to be easily inherited and used in tests.
class Simpleperf {
 public:
  explicit Simpleperf()
      : Simpleperf(CurrentProcess::dir(), DeviceInfo::is_emulator(),
                   DeviceInfo::is_user_build()) {}
  explicit Simpleperf(const std::string& simpleperf_dir, const bool is_emulator,
                      const bool is_user_build)
      : simpleperf_dir_(simpleperf_dir),
        is_emulator_(is_emulator),
        is_user_build_(is_user_build) {}
  ~Simpleperf() = default;

  // Invoke `simpleperf record` given the |pid| of the process to be profiled,
  // its corresponding package name, the path of the resulting trace file, and
  // the sampling interval. Also redirects stdout and stderr to a log file
  // located at |log_path|. The |abi_arch| determines the simpleperf binary to
  // use. The binary must match the abi of the app.
  void Record(int pid, const std::string& pkg_name, const std::string& abi_arch,
              const std::string& trace_path, int sampling_interval_us,
              const std::string& log_path) const;

  // Make sure profiling is enabled on the platform (otherwise LinuxSE prevents
  // it). Returns true on success.
  virtual bool EnableProfiling() const;

  // Kill simpleperf and returns true if it was killed successfully.
  // Technically this function could be const, but we mark it not for testing.
  // |pkg_name| represents the process name currently being profiled.
  virtual bool KillSimpleperf(int simpleperf_pid, const std::string& pkg_name);

  // Invokes `simpleperf report-sample` passing |input_path| as input file and
  // |output_path| as the protobuf output file. Adds the command output to
  // |output| and return true on success. The |abi_arch| determines the
  // simpleperf binary to use. The binary must match the abi of the app.
  virtual bool ReportSample(const std::string& input_path,
                            const std::string& output_path,
                            const std::string& abi_arch,
                            std::string* output) const;

 protected:
  // Returns a string with the full `simpleperf record` command, containing all
  // the flags and arguments passed.
  std::string GetRecordCommand(int pid, const std::string& pkg_name,
                               const std::string& abi_arch,
                               const std::string& trace_path,
                               int sampling_interval_us) const;

  // Split a simpleperf record command from a single string to an array of
  // strings. The original string should have its whitespaces removed, so the
  // resulting array doesn't contain any. For example:
  // |original_cmd|: "simpleperf record -p 13 -o test.data"
  // |split_cmd|: {"simpleperf", "record", "-p", "13", "-o", "test.data"}
  void SplitRecordCommand(char* original_cmd, char** split_cmd) const;

  // Returns a string with the features supported by this device.
  virtual std::string GetFeatures(const std::string& abi_arch) const;

 private:
  const std::string simpleperf_dir_;
  const bool is_emulator_;
  const bool is_user_build_;

  // Returns a string with the full simpleperf path (e.g. /path/simpleperf_arm).
  std::string GetSimpleperfPath(const std::string& abi_arch) const;
};
}  // namespace profiler

#endif  // PERFD_CPU_SIMPLEPERF_H_
