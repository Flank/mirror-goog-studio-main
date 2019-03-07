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
#include "perfd/cpu/cpu_config.h"

#include <gtest/gtest.h>
#include <cstdint>
#include <sstream>  // for std::ostringstream
#include <string>
#include "test/utils.h"
#include "utils/procfs_files.h"

using grpc::Status;
using profiler::ProcfsFiles;
using profiler::TestUtils;
using profiler::proto::CpuCoreConfigResponse;
using std::string;

namespace {

// A test-use-only class that uses checked-in files as test data to mock /proc
// files.
class MockProcfsFiles final : public ProcfsFiles {
 public:
  string GetSystemCpuPath() const override {
    return TestUtils::getCpuTestData("");
  }

  string GetSystemMinCpuFrequencyPath(int32_t cpu) const override {
    std::ostringstream os;
    os << "cpu" << cpu << "/scaling_min_freq.txt";
    return TestUtils::getCpuTestData(os.str());
  }

  string GetSystemMaxCpuFrequencyPath(int32_t cpu) const override {
    std::ostringstream os;
    os << "cpu" << cpu << "/scaling_max_freq.txt";
    return TestUtils::getCpuTestData(os.str());
  }
};

}  // namespace

namespace profiler {

TEST(CpuConfigTest, CpuCoreConfig) {
  CpuCoreConfigResponse response;
  MockProcfsFiles mock_fs;

  grpc::Status status = CpuConfig::GetCpuCoreConfig(mock_fs, &response);
  ASSERT_TRUE(status.ok());
  ASSERT_EQ(1, response.configs_size());
  auto core_config = response.configs(0);
  EXPECT_EQ(1, core_config.core());
  EXPECT_EQ(300000, core_config.min_frequency_in_khz());
  EXPECT_EQ(2000000, core_config.max_frequency_in_khz());
}

}  // namespace profiler