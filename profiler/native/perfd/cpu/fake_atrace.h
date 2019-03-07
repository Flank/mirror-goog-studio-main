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
#include <mutex>
#include <queue>
#include "perfd/cpu/atrace.h"
#include "utils/fake_clock.h"
#include "utils/tokenizer.h"

using std::string;
using testing::EndsWith;
using testing::Eq;
using testing::Ge;
using testing::Lt;

namespace profiler {

struct FakeAtraceParams {
  AtraceArgs args;
  bool is_running;
  bool fits_buffer;
};

// Helper class to validate RunAtrace calls. This class
// also takes in a function callback to perform additional tasks on
// RunAtrace calls.
class FakeAtrace final : public Atrace {
 public:
  FakeAtrace(Clock* clock) : FakeAtrace(clock, true) {}
  // We dont want to validate args if we are testing Atrace
  // via CpuService.
  FakeAtrace(Clock* clock, bool validate_args)
      : Atrace(clock),
        is_running_(false),
        buffer_size_kb_(8192),
        validate_args_(validate_args) {}

  void Run(const AtraceArgs& run_args) override {
    std::unique_lock<std::mutex> lock(mu_);
    if (validate_args_) {
      FakeAtraceParams& params = params_.front();
      EXPECT_EQ(params.args.app_pkg_name, run_args.app_pkg_name);
      EXPECT_EQ(params.args.path, run_args.path);
      EXPECT_EQ(params.args.command, run_args.command);
      EXPECT_EQ(params.args.additional_args, run_args.additional_args);
      is_running_ = params.is_running;
      params_.pop();
    } else {
      is_running_ = !is_running_;
    }
    cv_.notify_all();
  }

  bool IsAtraceRunning() override { return is_running_; }

  void WriteClockSyncMarker() override {}

  int GetBufferSizeKb() override { return buffer_size_kb_; }

  void SetBufferSize(int buffer_size_kb) { buffer_size_kb_ = buffer_size_kb; }

  virtual void Stop() override { is_running_ = false; }

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

  // Pushes the |params| to the back of a queue. As the test runs elements will
  // be poped off the queue in FIFO order.
  void EnqueueExpectedParams(FakeAtraceParams params) { params_.push(params); }

  void WaitUntilParamsSize(int count) {
    std::unique_lock<std::mutex> lock(mu_);
    EXPECT_TRUE(cv_.wait_for(lock, std::chrono::seconds(1), [this, count] {
      return params_.size() == count;
    }));
  }

 private:
  std::mutex mu_;
  std::condition_variable cv_;
  // Queue of arguments expected to hit fake atrace in FIFO order.
  std::queue<FakeAtraceParams> params_;
  bool is_running_;
  int buffer_size_kb_;
  bool validate_args_;
};

}  // namespace profiler
