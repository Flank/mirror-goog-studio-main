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
#include "perfd/cpu/atrace_manager.h"
#include "utils/fake_clock.h"
#include "utils/tokenizer.h"
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <condition_variable>
#include <queue>

using std::string;
using testing::EndsWith;
using testing::Ge;
using testing::Lt;

namespace profiler {

// Helper class to validate RunAtrace calls. This class
// also takes in a function callback to perform additional tasks on
// RunAtrace calls.
class FakeAtraceManager final : public AtraceManager {
 public:
  FakeAtraceManager(
      const Clock& clock,
      std::function<void(const std::string&, int)> write_data_callback)
      : AtraceManager(clock, 50), write_data_callback_(write_data_callback) {
    ResetState();
  }
  FakeAtraceManager(const Clock& clock)
      : FakeAtraceManager(clock, [](const std::string& path, int count) {}) {}

  // Override the RunAtrace function to not run Atrace but instead validate
  // The order of the calls, and run a function that allows each test to
  // determine the behavior of the atrace call.
  virtual void RunAtrace(const std::string& app_name, const std::string& path,
                         const std::string& command) override {
    std::unique_lock<std::mutex> lock(block_mutex_);
    block_var_.notify_one();

    // Each time we get a new command verify the state is the expected state.
    if (command.compare("--async_start") == 0) {
      EXPECT_FALSE(start_profiling_captured_);
      EXPECT_FALSE(stop_profiling_captured_);
      EXPECT_THAT(profiling_dumps_captured_, 0);
      start_profiling_captured_ = true;
    } else if (command.compare("--async_stop") == 0) {
      EXPECT_TRUE(start_profiling_captured_);
      EXPECT_FALSE(stop_profiling_captured_);
      stop_profiling_captured_ = true;
      EXPECT_THAT(GetDumpCount(), profiling_dumps_captured_ + 1);
      ValidatePath(path);
      write_data_callback_(path, profiling_dumps_captured_);
    } else if (command.compare("--async_dump") == 0) {
      EXPECT_TRUE(start_profiling_captured_);
      EXPECT_FALSE(stop_profiling_captured_);
      ValidatePath(path);
      write_data_callback_(path, profiling_dumps_captured_);
      profiling_dumps_captured_++;
    }
  }

  virtual std::string BuildSupportedCategoriesString() override {
    std::string atrace_output(
        "gfx - Graphics\n"
        "    input - Input\n"
        "     view - View System\n"
        "  webview - WebView\n"
        "       wm - Window Manager\n"
        "       am - Activity Manager\n"
        "       sm - Sync Manager");
    std::set<std::string> categories = ParseListCategoriesOutput(atrace_output);
    EXPECT_THAT(categories, testing::Contains("gfx"));
    EXPECT_THAT(categories, testing::Contains("wm"));
    EXPECT_THAT(categories, testing::Contains("am"));
    EXPECT_THAT(categories, testing::Contains("sm"));
    EXPECT_THAT(categories, testing::Contains("webview"));
    EXPECT_THAT(categories, testing::Contains("view"));
    EXPECT_THAT(categories.find("video"), testing::Eq(categories.end()));
    return " gfx input view webview wm am sm";
  }

  // This function blocks until we have at minimum [count] traces, it is
  // possible that the count will be greater than [count].
  void BlockForXTraces(int count) {
    std::unique_lock<std::mutex> lock(block_mutex_);
    while (count-- > 0) {
      block_var_.wait(lock);
    }
  }

  void ValidatePath(const std::string& path) {
    EXPECT_THAT(path, EndsWith(std::to_string(profiling_dumps_captured_)));
  }

  std::string GetTracePath(const std::string& app_name) const override {
    std::ostringstream path;
    path << getenv("TEST_TMPDIR") << "/"
         << ::testing::UnitTest::GetInstance()->current_test_info()->name()
         << ".atrace";
    return path.str();
  }

  void ResetState() {
    stop_profiling_captured_ = false;
    start_profiling_captured_ = false;
    profiling_dumps_captured_ = 0;
  }

 private:
  std::mutex block_mutex_;
  std::condition_variable block_var_;
  std::function<void(const std::string&, int)> write_data_callback_;
  bool start_profiling_captured_;
  bool stop_profiling_captured_;
  int profiling_dumps_captured_;
};

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
  FakeAtraceManager atrace(test_data.fake_clock);
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000,
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
  FakeAtraceManager atrace(test_data.fake_clock);
  for (int i = 0; i < 3; i++) {
    EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000,
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
  FakeAtraceManager atrace(test_data.fake_clock);
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000,
                                    &test_data.trace_path, &test_data.error));
  atrace.BlockForXTraces(1);
  EXPECT_THAT(atrace.GetDumpCount(), Ge(1));
  EXPECT_TRUE(atrace.IsProfiling());
  EXPECT_FALSE(atrace.StartProfiling(test_data.app_name, 1000,
                                     &test_data.trace_path, &test_data.error));

  EXPECT_THAT(atrace.GetDumpCount(), Ge(1));
  EXPECT_TRUE(atrace.StopProfiling(test_data.app_name, true, &test_data.error));
}

TEST(AtraceManagerTest, StopProfilingCombinesFiles) {
  TestInitializer test_data;
  int dump_count = 3;
  // Tell our mock Atrace manager that for each "atrace" run we want to create a
  // file and write how many dumps have been created to this point to the file.
  FakeAtraceManager atrace(test_data.fake_clock,
                           [](const std::string& path, int count) {
    FILE* file = fopen(path.c_str(), "wb");
    fwrite(&count, sizeof(int), 1, file);
    fclose(file);
  });
  EXPECT_TRUE(atrace.StartProfiling(test_data.app_name, 1000,
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
