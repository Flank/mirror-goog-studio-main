/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <gtest/gtest.h>
#include <unistd.h>
#include <condition_variable>
#include <mutex>
#include <string>
#include <vector>

#include "nonblocking_command_runner.h"

using profiler::NonBlockingCommandRunner;

const char* kCatPath = "/bin/cat";
const char* kArguments[] = {kCatPath, NULL};

// Helper class to handle getting some output and validating it.
class OutputValidator {
 public:
  OutputValidator(const std::string& expected) : expected_(expected) {}

  void Validate(int stdout_fd) {
    std::unique_lock<std::mutex> lock(output_mutex_);
    size_t size = expected_.size();
    std::vector<char> buffer(size);
    EXPECT_EQ(size, read(stdout_fd, buffer.data(), size));
    EXPECT_EQ(expected_, std::string(buffer.begin(), buffer.end()));
    output_cv_.notify_all();
  }

  void Wait() {
    std::unique_lock<std::mutex> lock(output_mutex_);
    output_cv_.wait(lock);
  }

 private:
  std::mutex output_mutex_;
  std::condition_variable output_cv_;
  std::string expected_;
};

TEST(NonBlockingCommandRunnerTest, TestInputIsAsync) {
  NonBlockingCommandRunner cat(kCatPath);
  std::string input("Some Input");
  EXPECT_TRUE(cat.Run(kArguments, input));
  EXPECT_TRUE(cat.IsRunning());
  cat.Kill();
  EXPECT_FALSE(cat.IsRunning());
}

TEST(NonBlockingCommandRunnerTest, TestInputAndOutput) {
  NonBlockingCommandRunner cat(kCatPath);
  std::string input("Some Input");
  // Setup helper class and thread to handle output and kill process.
  OutputValidator handler(input);
  NonBlockingCommandRunner::StdoutCallback output_handler =
      std::bind(&OutputValidator::Validate, &handler, std::placeholders::_1);
  EXPECT_TRUE(cat.Run(kArguments, input, &output_handler));
  // Wait until we validate our expected output.
  handler.Wait();
  // Kill the process
  cat.Kill();
  // Validate we killed the process.
  EXPECT_FALSE(cat.IsRunning());
  // Note: If we leave the thread open in the NonBlockingCommandRunner the test
  // will fail.
}