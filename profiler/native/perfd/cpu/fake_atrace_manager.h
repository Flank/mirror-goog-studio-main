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
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <condition_variable>
#include <queue>
#include "perfd/cpu/atrace_manager.h"
#include "utils/fake_clock.h"
#include "utils/tokenizer.h"

using std::string;
using testing::EndsWith;
using testing::Eq;
using testing::Ge;
using testing::Lt;

namespace profiler {

// Helper class to validate RunAtrace calls. This class
// also takes in a function callback to perform additional tasks on
// RunAtrace calls.
class FakeAtraceManager final : public AtraceManager {
 public:
  FakeAtraceManager(
      Clock* clock,
      std::function<void(const std::string&, int)> write_data_callback)
      : AtraceManager(clock, 50), write_data_callback_(write_data_callback) {
    ResetState();
  }
  FakeAtraceManager(Clock* clock)
      : FakeAtraceManager(clock, [](const std::string& path, int count) {}) {}

  // Override the RunAtrace function to not run Atrace but instead validate
  // The order of the calls, and run a function that allows each test to
  // determine the behavior of the atrace call.
  virtual void RunAtrace(const std::string& app_name, const std::string& path,
                         const std::string& command,
                         const std::string& additional_args) override {
    std::unique_lock<std::mutex> lock(block_mutex_);
    block_var_.notify_one();
    if (forced_running_state_ != -1) {
      // If we are forcing the running state to emulate errors, then we don't
      // need the test to validate the internal state.
      return;
    }

    // Each time we get a new command verify the state is the expected state.
    if (command.compare("--async_start") == 0) {
      EXPECT_FALSE(start_profiling_captured_);
      EXPECT_FALSE(stop_profiling_captured_);
      EXPECT_FALSE(clock_sync_write_);
      EXPECT_THAT(profiling_dumps_captured_, 0);
      EXPECT_THAT(additional_args, testing::Eq("-b 8192"));
      start_profiling_captured_ = true;
    } else if (command.compare("--async_stop") == 0) {
      EXPECT_TRUE(start_profiling_captured_);
      EXPECT_FALSE(stop_profiling_captured_);
      EXPECT_TRUE(clock_sync_write_);
      stop_profiling_captured_ = true;
      EXPECT_THAT(GetDumpCount(), profiling_dumps_captured_ + 1);
      ValidatePath(path);
      write_data_callback_(path, profiling_dumps_captured_);
    } else if (command.compare("--async_dump") == 0) {
      EXPECT_TRUE(start_profiling_captured_);
      EXPECT_FALSE(stop_profiling_captured_);
      EXPECT_THAT(additional_args, testing::Eq("-b 8192"));
      ValidatePath(path);
      write_data_callback_(path, profiling_dumps_captured_);
      profiling_dumps_captured_++;
    }
  }

  virtual void WriteClockSyncMarker() override { clock_sync_write_ = true; }

  virtual bool IsAtraceRunning() override {
    if (forced_running_state_ == -1) {
      return start_profiling_captured_ && !stop_profiling_captured_;
    }
    return forced_running_state_;
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
    clock_sync_write_ = false;
    profiling_dumps_captured_ = 0;
    forced_running_state_ = -1;
  }

  void ForceRunningState(bool isRunning) {
    forced_running_state_ = isRunning ? 1 : 0;
  }

 private:
  std::mutex block_mutex_;
  std::condition_variable block_var_;
  std::function<void(const std::string&, int)> write_data_callback_;
  bool start_profiling_captured_;
  bool stop_profiling_captured_;
  // -1 is not set, 0, is false, 1 is true.
  int forced_running_state_;
  int profiling_dumps_captured_;
  bool clock_sync_write_;
};

}  // namespace profiler
