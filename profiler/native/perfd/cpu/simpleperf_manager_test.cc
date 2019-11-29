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
#include "perfd/cpu/fake_simpleperf.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

using profiler::proto::TraceStopStatus;
using std::string;
using testing::HasSubstr;

namespace profiler {

class FakeFileSystem : public MemoryFileSystem {
 public:
  FakeFileSystem(bool move_file_success)
      : MemoryFileSystem(), move_file_success_(move_file_success) {}
  bool MoveFile(const std::string &path_from,
                const std::string &path_to) override {
    return move_file_success_;
  }

 private:
  bool move_file_success_;
};

TEST(SimpleperfManagerTest, StartProfiling) {
  SimpleperfManager simpleperf_manager(
      std::unique_ptr<Simpleperf>(new FakeSimpleperf()));
  string error;
  string fake_trace_path = "/tmp/fake-trace";
  string app_name = "some_app_name";
  string abi = "arm";

  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  // Should not be able to start profiling twice.
  EXPECT_FALSE(simpleperf_manager.StartProfiling(app_name, abi, 1000,
                                                 fake_trace_path, &error));
}

TEST(SimpleperfManagerTest, StartStartupProfiling) {
  SimpleperfManager simpleperf_manager(
      std::unique_ptr<Simpleperf>(new FakeSimpleperf()));
  string error;
  string fake_trace_path = "/tmp/fake-trace";
  string app_name = "some_app_name";
  string abi = "arm";

  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error, true);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StartProfilingWithoutProfilingEnabled) {
  std::unique_ptr<FakeSimpleperf> simpleperf{new FakeSimpleperf()};
  // Simulate a failure when trying to enable profiling on the device.
  // That should cause |StartProfiling| to fail.
  simpleperf->SetEnableProfilingSuccess(false);
  SimpleperfManager simpleperf_manager(std::move(simpleperf));

  string error;
  string fake_trace_path = "/tmp/fake-trace";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
  EXPECT_THAT(error, HasSubstr("Unable to setprop to enable profiling"));
}

TEST(SimpleperfManagerTest, StopProfilingWantResult) {
  SimpleperfManager simpleperf_manager(
      std::unique_ptr<Simpleperf>(new FakeSimpleperf()));
  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  auto result = simpleperf_manager.StopProfiling(app_name, true, &error);
  EXPECT_THAT(result, testing::Eq(TraceStopStatus::SUCCESS));
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StopProfilingNotProfiledApp) {
  SimpleperfManager simpleperf_manager(
      std::unique_ptr<Simpleperf>(new FakeSimpleperf()));
  string error;
  string app_name = "app";  // App that is not currently being profiled

  auto result = simpleperf_manager.StopProfiling(app_name, true, &error);
  EXPECT_THAT(result, testing::Eq(TraceStopStatus::NO_ONGOING_PROFILING));
  EXPECT_THAT(error, HasSubstr("This app was not being profiled"));
}

TEST(SimpleperfManagerTest, StopProfilingFailToKillSimpleperf) {
  std::unique_ptr<FakeSimpleperf> simpleperf{new FakeSimpleperf()};
  // Simulate a failure when trying to kill simpleperf.
  // That should cause |StopProfiling| to fail.
  simpleperf->SetKillSimpleperfSuccess(false);
  SimpleperfManager simpleperf_manager(std::move(simpleperf));

  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  auto result = simpleperf_manager.StopProfiling(app_name, true, &error);
  EXPECT_THAT(result, testing::Eq(TraceStopStatus::STOP_COMMAND_FAILED));
  EXPECT_THAT(error, HasSubstr("Failed to send SIGTERM to simpleperf"));
  // TODO (b/67630133): decide if we should keep profiling the app if we fail to
  // kill simpleperf.
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StopProfilingFailToCopyRawFile) {
  std::unique_ptr<FakeSimpleperf> simpleperf{new FakeSimpleperf()};
  // Simulate a failure when trying to convert the simpleperf raw trace file to
  // protobuf format, which happens inside |ConvertRawToProto|. That should
  // cause |StopProfiling| to fail.
  simpleperf->SetReportSampleSuccess(false);
  SimpleperfManager simpleperf_manager(
      std::move(simpleperf),
      std::unique_ptr<FileSystem>(new FakeFileSystem(false)));

  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  auto result = simpleperf_manager.StopProfiling(app_name, true, &error);
  EXPECT_THAT(result, testing::Eq(TraceStopStatus::CANNOT_COPY_FILE));
  EXPECT_THAT(error, HasSubstr("Unable to copy simpleperf raw trace."));
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StopProfilingNotWantResult) {
  SimpleperfManager simpleperf_manager(
      std::unique_ptr<Simpleperf>(new FakeSimpleperf()));
  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "x86";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  auto result = simpleperf_manager.StopProfiling(app_name, false, &error);
  EXPECT_THAT(result, testing::Eq(TraceStopStatus::SUCCESS));
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, StopSimpleperfFailToKillSimpleperf) {
  std::unique_ptr<FakeSimpleperf> simpleperf{new FakeSimpleperf()};
  // Simulate a failure when trying to kill simpleperf.
  // That should cause |StopSimpleperf| to fail.
  simpleperf->SetKillSimpleperfSuccess(false);
  SimpleperfManager simpleperf_manager(std::move(simpleperf));

  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "x86_64";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  EXPECT_TRUE(simpleperf_manager.IsProfiling(app_name));

  auto result = simpleperf_manager.StopProfiling(app_name, false, &error);
  EXPECT_THAT(result, testing::Eq(TraceStopStatus::STOP_COMMAND_FAILED));
  // If something goes wrong when we try to kill simpleplerf, we write that to
  // |error| and propagate it to the logs (CpuService will do the logging)
  EXPECT_THAT(error, HasSubstr("Failed to send SIGTERM to simpleperf"));
  EXPECT_FALSE(simpleperf_manager.IsProfiling(app_name));
}

TEST(SimpleperfManagerTest, ReportSampleNotCalledIfRunningOnHost) {
  std::unique_ptr<FakeSimpleperf> simpleperf{new FakeSimpleperf()};
  SimpleperfManager simpleperf_manager(std::move(simpleperf));

  string error;
  string fake_trace_path = "/tmp/trace_path";
  string app_name = "some_app_name";
  string abi = "arm";

  simpleperf_manager.StartProfiling(app_name, abi, 1000, fake_trace_path,
                                    &error);
  simpleperf_manager.StopProfiling(app_name, true, &error);

  auto *fake_simpleperf =
      dynamic_cast<FakeSimpleperf *>(simpleperf_manager.simpleperf());
  // ReportSample should not be called, as report-sample will be done on host
  EXPECT_FALSE(fake_simpleperf->GetReportSampleCalled());
}

}  // namespace profiler
