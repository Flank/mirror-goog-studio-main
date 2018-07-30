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
#include "perfd/cpu/simpleperf.h"
#include "utils/fake_clock.h"

#include "utils/log.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace {
using std::string;
}

namespace profiler {

// A subclass of Simpleperf to be used in tests. All the methods are noop and
// return either true (success) or false (failure). This way we can test
// |SimpleperfManager| without caring too much about implementation details of
// |Simpleperf|.
class FakeSimpleperf final : public Simpleperf {
 public:
  explicit FakeSimpleperf()
      : Simpleperf("/fake/path/", false, true),
        enable_profiling_success_(true),
        kill_simpleperf_success_(true),
        report_sample_success_(true),
        kill_simpleperf_called_(false),
        report_sample_called_(false) {}

  bool EnableProfiling() const { return enable_profiling_success_; }

  bool KillSimpleperf(int simpleperf_pid, const string& pkg_name) {
    kill_simpleperf_called_ = true;
    return kill_simpleperf_success_;
  }

  bool ReportSample(const string& input_path, const string& output_path,
                    const string& abi_arch, string* output) {
    report_sample_called_ = true;
    return report_sample_success_;
  }

  void SetEnableProfilingSuccess(bool success) {
    enable_profiling_success_ = success;
  }

  void SetKillSimpleperfSuccess(bool success) {
    kill_simpleperf_success_ = success;
  }

  void SetReportSampleSuccess(bool success) {
    report_sample_success_ = success;
  }

  bool GetKillSimpleperfCalled() { return kill_simpleperf_called_; }

  bool GetReportSampleCalled() { return report_sample_called_; }

 private:
  bool enable_profiling_success_;
  bool kill_simpleperf_success_;
  bool report_sample_success_;
  bool kill_simpleperf_called_;
  bool report_sample_called_;
};

}  // namespace profiler
