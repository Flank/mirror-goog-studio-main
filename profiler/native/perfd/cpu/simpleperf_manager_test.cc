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
#include "perfd/cpu/simpleperf_manager.h"
#include "perfd/cpu/simpleperf.h"
#include "utils/fake_clock.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

using std::string;
using testing::HasSubstr;

namespace profiler {

// A subclass of Simpleperf to be used in tests. All the methods are noop and
// return either true (success) or false (failure). This way we can test
// |SimpleperfManager| without caring too much about implementation details of
// |Simpleperf|.
class FakeSimpleperf final : public Simpleperf {
 public:
  explicit FakeSimpleperf()
      : Simpleperf("/fake/path/", false),
        enable_profiling_success_(true),
        kill_simpleperf_success_(true),
        report_sample_success_(true) {}

  bool EnableProfiling() const { return enable_profiling_success_; }

  bool KillSimpleperf(int simpleperf_pid) const {
    return kill_simpleperf_success_;
  }

  bool ReportSample(const string& input_path, const string& output_path,
                    const string& abi_arch, string* output) const {
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

 private:
  bool enable_profiling_success_;
  bool kill_simpleperf_success_;
  bool report_sample_success_;
};

TEST(SimpleperfManagerTest, StartProfiling) {
  FakeClock fake_clock(0);
  FakeSimpleperf simpleperf;
  SimpleperfManager simpleperf_manager(fake_clock, simpleperf);
  string error;
  string fake_trace_path = "/tmp/fake-trace";
  string app_name = "some_app_name";
  string abi = "arm";

  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
  simpleperf_manager.StartProfiling(app_name, abi, 1000, &fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StartProfilingWithoutProfilingEnabled) {
  FakeClock fake_clock(0);
  FakeSimpleperf simpleperf;
  // Simulate a failure when trying to enable profiling on the device.
  // That should cause |StartProfiling| to fail.
  simpleperf.SetEnableProfilingSuccess(false);
  SimpleperfManager simpleperf_manager(fake_clock, simpleperf);

  string error;
  string fake_trace_path = "/tmp/fake-trace";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, &fake_trace_path,
                                    &error);
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
  EXPECT_THAT(error, HasSubstr("Unable to setprop to enable profiling"));
}

TEST(SimpleperfManagerTest, StopProfilingProfiledApp) {
  FakeClock fake_clock(0);
  FakeSimpleperf simpleperf;
  SimpleperfManager simpleperf_manager(fake_clock, simpleperf);
  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, &fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  bool result = simpleperf_manager.StopProfiling(app_name, &error);
  EXPECT_TRUE(result);
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StopProfilingNotProfiledApp) {
  FakeClock fake_clock(0);
  FakeSimpleperf simpleperf;
  SimpleperfManager simpleperf_manager(fake_clock, simpleperf);
  string error;
  string app_name = "app";  // App that is not currently being profiled

  bool result = simpleperf_manager.StopProfiling(app_name, &error);
  EXPECT_FALSE(result);
  EXPECT_THAT(error, HasSubstr("This app was not being profiled"));
}

TEST(SimpleperfManagerTest, StopProfilingFailToKillSimpleperf) {
  FakeClock fake_clock(0);
  FakeSimpleperf simpleperf;
  // Simulate a failure when trying to kill simpleperf.
  // That should cause |StopProfiling| to fail.
  simpleperf.SetKillSimpleperfSuccess(false);
  SimpleperfManager simpleperf_manager(fake_clock, simpleperf);

  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, &fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  bool result = simpleperf_manager.StopProfiling(app_name, &error);
  EXPECT_FALSE(result);
  EXPECT_THAT(error, HasSubstr("Failed to send SIGTERM to simpleperf"));
  // TODO (b/67630133): decide if we should keep profiling the app if we fail to
  // kill simpleperf.
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StopProfilingFailToConvertProto) {
  FakeClock fake_clock(0);
  FakeSimpleperf simpleperf;
  // Simulate a failure when trying to convert the simpleperf raw trace file to
  // protobuf format, which happens inside |ConvertRawToProto|. That should
  // cause |StopProfiling| to fail.
  simpleperf.SetReportSampleSuccess(false);
  SimpleperfManager simpleperf_manager(fake_clock, simpleperf);

  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, &fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  bool result = simpleperf_manager.StopProfiling(app_name, &error);
  EXPECT_FALSE(result);
  EXPECT_THAT(error, HasSubstr("Unable to generate simpleperf report"));
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

}  // namespace profiler
