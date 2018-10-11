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
#include "tools/base/deploy/installer/shell_command.h"
#include <fcntl.h>
#include <gtest/gtest.h>

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
  ShellCommandRunner runner("sh");
  string output;
  runner.Run("-c \"echo Hello\"", &output);
  ASSERT_EQ("Hello\n", output);
}

TEST_F(ShellCommandRunnerTest, TestPiped) {
  // Set the size to more than 64K so pipes block
  int size = 64 * 1024 + 1;

  char* buffer0 = alloc("01234", 5, size);
  char* buffer1 = alloc("abcde", 5, size);
  char* buffer2 = alloc("ABCDE", 5, size);

  string tmp = std::getenv("TEST_TMPDIR");
  tmp += "/stdin.XXXXXX";
  char* name = strdup(tmp.c_str());
  int temp_fd = mkstemp(name);
  write(temp_fd, buffer0, size);
  write(temp_fd, buffer1, size);
  write(temp_fd, buffer2, size);
  close(temp_fd);
  tmp = name;
  free(name);

  string output, error;
  ShellCommandRunner runner(helper_path);
  vector<string> args;
  std::stringstream string_size;
  string_size << size;
  args.push_back(string_size.str());
  runner.Run(args, tmp, &output, &error);
  ASSERT_EQ(size * 3 + 3, output.size());
  EXPECT_EQ(0, strncmp(output.data(), buffer0, size));
  EXPECT_EQ(0, strncmp(output.data() + size + 1, buffer1, size));
  EXPECT_EQ(0, strncmp(output.data() + size + size + 2, buffer2, size));

  ASSERT_EQ(size * 2 + 2, error.size());
  EXPECT_EQ(0, strncmp(error.data(), buffer0, size));
  EXPECT_EQ(0, strncmp(error.data() + size + 1, buffer1, size));

  free(buffer0);
  free(buffer1);
  free(buffer2);
}

}  // namespace deploy

int main(int argc, char** argv) {
  testing::InitGoogleTest(&argc, argv);
  if (argc > 1) {
    helper_path = argv[1];
  }
  return RUN_ALL_TESTS();
}
