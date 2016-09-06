/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include "file_reader.h"

#include <gtest/gtest.h>

// When using bazel to build, testdata directory needs to be set, since the
// files are not moved to the root, as it happens when using CMake.
#ifdef BAZEL
#define UTILS_TEST_DATA_ROOT "tools/base/profiler/native/testdata/utils/"
#else
#define UTILS_TEST_DATA_ROOT ""
#endif

using profiler::FileReader;

TEST(Read, FileSizeIsSmallerThanPageSize) {
  std::string content;
  FileReader::Read(UTILS_TEST_DATA_ROOT "file_reader_small.txt", &content);
  EXPECT_EQ(37u, content.size());
}

TEST(Read, ReadFileSizeLargerThanBufferSize) {
  std::string content;
  FileReader::Read(UTILS_TEST_DATA_ROOT "file_reader_large.txt", &content);
  EXPECT_EQ(5264u, content.size());
}

TEST(Read, ReadFileAbsent) {
  std::string content;
  EXPECT_FALSE(FileReader::Read(UTILS_TEST_DATA_ROOT "file_reader_absent.txt", &content));
}

TEST(ReadToLines, MultipleLineBreakChars) {
  std::vector<std::string> lines;
  FileReader::Read(UTILS_TEST_DATA_ROOT "file_reader_multiple_lines.txt", &lines);
  EXPECT_EQ(2u, lines.size());
  EXPECT_EQ("It contains two lines.", lines.at(0));
  EXPECT_EQ("This is the second line.", lines.at(1));
}
