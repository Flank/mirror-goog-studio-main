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
#include "perfd/cpu/fake_atrace_manager.h"
#include "utils/fake_clock.h"
#include "utils/tokenizer.h"
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <condition_variable>
#include <queue>

using std::string;
using testing::EndsWith;
using testing::Eq;
using testing::Ge;
using testing::Lt;

namespace profiler {

// Simple helper struct to define test data used across multiple test.
struct TestInitializer {
 public:
  TestInitializer() : fake_clock(0), app_name("Fake_App") {}
  FakeClock fake_clock;
  std::string app_name;
  std::string trace_path;
  std::string error;
};

TEST(AtraceManagerTest, ProfilingStartStop) {
  TestInitializer test_data;
  int dump_count = 3;
  FakeAtraceManager atrace(&test_data.fake_clock);
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                    &test_data.trace_path, &test_data.error));
  EXPECT_TRUE(atrace.IsProfiling());
  atrace.BlockForXTraces(dump_count);
  EXPECT_THAT(atrace.GetDumpCount(), Ge(dump_count));
  EXPECT_TRUE(atrace.StopProfiling(test_data.app_name, true, &test_data.error));
  EXPECT_FALSE(atrace.IsProfiling());
}

TEST(AtraceManagerTest, ProfilerReentrant) {
  TestInitializer test_data;
  int dump_count = 3;
  FakeAtraceManager atrace(&test_data.fake_clock);
  for (int i = 0; i < 3; i++) {
    EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                      &test_data.trace_path, &test_data.error));
    EXPECT_THAT(atrace.GetDumpCount(), Lt(dump_count));
    EXPECT_TRUE(atrace.IsProfiling());
    atrace.BlockForXTraces(dump_count);
    EXPECT_THAT(atrace.GetDumpCount(), Ge(dump_count));
    EXPECT_TRUE(
        atrace.StopProfiling(test_data.app_name, false, &test_data.error));
    EXPECT_FALSE(atrace.IsProfiling());
    atrace.ResetState();
  }
}

TEST(AtraceManagerTest, ProfilingStartTwice) {
  TestInitializer test_data;
  FakeAtraceManager atrace(&test_data.fake_clock);
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                    &test_data.trace_path, &test_data.error));
  atrace.BlockForXTraces(1);
  EXPECT_THAT(atrace.GetDumpCount(), Ge(1));
  EXPECT_TRUE(atrace.IsProfiling());
  EXPECT_FALSE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                     &test_data.trace_path, &test_data.error));

  EXPECT_THAT(atrace.GetDumpCount(), Ge(1));
  EXPECT_TRUE(atrace.StopProfiling(test_data.app_name, true, &test_data.error));
}

TEST(AtraceManagerTest, StartStopFailsAndReturnsError) {
  TestInitializer test_data;
  FakeAtraceManager atrace(&test_data.fake_clock);
  atrace.ForceRunningState(false);
  EXPECT_FALSE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                     &test_data.trace_path, &test_data.error));
  EXPECT_THAT(test_data.error, Eq("Failed to run atrace start."));

  test_data.error.clear();
  atrace.ResetState();
  // Start profiling
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                    &test_data.trace_path, &test_data.error));
  // Fail to stop profiling
  atrace.ForceRunningState(true);
  EXPECT_FALSE(
      atrace.StopProfiling(test_data.app_name, false, &test_data.error));
  EXPECT_THAT(test_data.error, Eq("Failed to stop atrace."));

  test_data.error.clear();
  atrace.ResetState();
  // Start profiling
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                    &test_data.trace_path, &test_data.error));
  // Fail to stop profiling, this time we expect a result however the error
  // should be the same.
  atrace.ForceRunningState(true);
  EXPECT_FALSE(
      atrace.StopProfiling(test_data.app_name, true, &test_data.error));
  EXPECT_THAT(test_data.error, Eq("Failed to stop atrace."));
}

TEST(AtraceManagerTest, StopProfilingCombinesFiles) {
  TestInitializer test_data;
  int dump_count = 3;
  // Tell our mock Atrace manager that for each "atrace" run we want to create a
  // file and write how many dumps have been created to this point to the file.
  FakeAtraceManager atrace(&test_data.fake_clock,
                           [](const std::string& path, int count) {
    FILE* file = fopen(path.c_str(), "wb");
    fwrite(&count, sizeof(int), 1, file);
    fclose(file);
  });
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000, 8,
                                    &test_data.trace_path, &test_data.error));
  EXPECT_THAT(atrace.GetDumpCount(), Ge(0));
  atrace.BlockForXTraces(dump_count);
  EXPECT_TRUE(atrace.StopProfiling(test_data.app_name, true, &test_data.error));

  // On stop profiling get the dump count (this is incremented by stop
  // profiling)
  int total_dumps = atrace.GetDumpCount();

  // Open the final output file and validate that it contains the contents of
  // each individual dump file.
  FILE* file = fopen(test_data.trace_path.c_str(), "rb");
  int value = 0;
  for (int i = 0; i < total_dumps; i++) {
    int read_amount = fread(&value, sizeof(int), 1, file);
    EXPECT_THAT(value, i);
    EXPECT_THAT(read_amount, 1);
  }
  int read_amount = fread(&value, sizeof(int), 1, file);
  EXPECT_THAT(read_amount, 0);
  fclose(file);
}
}  // namespace profiler
