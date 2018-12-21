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
#include "memory_usage_reader_impl.h"
#include "proto/memory.pb.h"
#include "test/utils.h"
#include "utils/file_reader.h"

#include <gtest/gtest.h>

using profiler::TestUtils;

TEST(ParseMemoryLevels, MemoryDataVersion3Valid) {
  std::string content;
  profiler::FileReader::Read(
      TestUtils::getMemoryTestData("memory_data_valid_v3.txt"), &content);

  profiler::MemoryUsageReaderImpl reader;
  profiler::proto::MemoryUsageData sample;
  reader.ParseMemoryLevels(content, &sample);

  // The following values are obtained from the same dumpsys that generated the
  // test data file.
  EXPECT_EQ(9336, sample.total_mem());
  EXPECT_EQ(2092, sample.java_mem());
  EXPECT_EQ(3496, sample.native_mem());
  EXPECT_EQ(128, sample.stack_mem());
  EXPECT_EQ(0, sample.graphics_mem());
  EXPECT_EQ(3284, sample.code_mem());
  EXPECT_EQ(336, sample.others_mem());
}

TEST(ParseMemoryLevels, MemoryDataVersion4Valid) {
  std::string content;
  profiler::FileReader::Read(
      TestUtils::getMemoryTestData("memory_data_valid_v4.txt"), &content);

  profiler::MemoryUsageReaderImpl reader;
  profiler::proto::MemoryUsageData sample;
  reader.ParseMemoryLevels(content, &sample);

  // The following values are obtained from the same dumpsys that generated the
  // test data file.
  EXPECT_EQ(9684, sample.total_mem());
  EXPECT_EQ(2588, sample.java_mem());
  EXPECT_EQ(3844, sample.native_mem());
  EXPECT_EQ(188, sample.stack_mem());
  EXPECT_EQ(0, sample.graphics_mem());
  EXPECT_EQ(2664, sample.code_mem());
  EXPECT_EQ(400, sample.others_mem());
}
