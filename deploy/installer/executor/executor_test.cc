/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor/executor_impl.h"

#include <fcntl.h>
#include <gtest/gtest.h>
#include <unistd.h>

using std::string;
using std::vector;

string helper_path;

namespace deploy {

class ShellCommandRunnerTest : public ::testing::Test {
 public:
  ShellCommandRunnerTest() {}
  void SetUp() override {}

  void TearDown() override {}

  char* alloc(const char* pattern, size_t pattern_size, size_t size) {
    char* b = (char*)malloc(size);
    for (int i = 0; i < size; i++) {
      b[i] = pattern[i % pattern_size];
    }
    return b;
  }
};

TEST_F(ShellCommandRunnerTest, TestSimpleRun) {
  string output;
  string error;
  std::vector<std::string> args;
  args.emplace_back("-c");
  ExecutorImpl executor;
  args.emplace_back("echo \"Hello\"");
  executor.Run("sh", args, &output, &error);
  ASSERT_EQ("Hello\n", output);
}

TEST_F(ShellCommandRunnerTest, TestForkExitIfExecFails) {
  // This test times out if the child process is not killed before Run()
  // returns. This is accomplished by leveraging the fact that a read on a pipe
  // will block if the write end is still open.
  int fds[2];
  pipe(fds);

  std::string output, error;
  ExecutorImpl executor;
  executor.Run("missing_executable", {}, &output, &error);
  close(fds[1]);

  char buf;
  read(fds[0], &buf, 1);
}

TEST_F(ShellCommandRunnerTest, TestForkAndExec) {
  ExecutorImpl executor;

  int input, output, pid;
  executor.ForkAndExec("sh", {"-c", "cat"}, &input, &output, nullptr, &pid);

  ASSERT_EQ(5, write(input, "Hello", 5));
  ASSERT_EQ(0, close(input));
  ASSERT_EQ(pid, waitpid(pid, nullptr, 0));

  char buffer[6] = {'\0'};
  ASSERT_EQ(5, read(output, buffer, 5));
  ASSERT_EQ("Hello", std::string(buffer));
}

TEST_F(ShellCommandRunnerTest, TestForkAndExecWithFds) {
  ExecutorImpl executor;

  int fds[2];
  pipe(fds);

  ASSERT_EQ(5, write(fds[1], "Hello", 5));
  fcntl(fds[1], F_SETFD, FD_CLOEXEC);

  int output, error, pid;
  executor.ForkAndExecWithStdinFd("sh", {"-c", "cat"}, fds[0], &output, &error,
                                  &pid);

  ASSERT_EQ(2, write(fds[1], "!!", 2));
  ASSERT_EQ(0, close(fds[1]));
  ASSERT_EQ(pid, waitpid(pid, nullptr, 0));

  char buffer[8] = {'\0'};
  ASSERT_EQ(7, read(output, buffer, 7));
  ASSERT_EQ("Hello!!", std::string(buffer));
}

int main(int argc, char** argv) {
  testing::InitGoogleTest(&argc, argv);
  if (argc > 1) {
    helper_path = argv[1];
  }
  return RUN_ALL_TESTS();
}
}  // namespace deploy
