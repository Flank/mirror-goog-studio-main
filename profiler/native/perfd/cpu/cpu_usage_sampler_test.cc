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
#include "perfd/cpu/cpu_usage_sampler.h"

#include <gtest/gtest.h>
#include <cstdint>
#include <sstream>  // for std::ostringstream
#include <string>
#include <vector>
#include "perfd/cpu/cpu_cache.h"
#include "perfd/cpu/procfs_files.h"
#include "perfd/daemon.h"
#include "test/utils.h"

using std::vector;
using std::string;
using std::unique_ptr;

namespace profiler {

// Tests in this file assume a time unit in /proc files is 10 milliseconds.

// A test-use-only class that uses checked-in files as test data to mock /proc
// files.
class MockProcfsFiles final : public ProcfsFiles {
 public:
  std::string GetSystemStatFilePath() const override {
    return TestUtils::getCpuTestData("proc_stat_1.txt");
  }
  std::string GetProcessStatFilePath(int32_t pid) const override {
    std::ostringstream os;
    os << "pid_stat_" << pid << ".txt";
    return TestUtils::getCpuTestData(os.str());
  }
};

class CpuUsageSamplerToTest final : public CpuUsageSampler {
 public:
  // Replace the real procfs by a mock one for testing, making it possible
  // to run this test on systems that do not have /proc such as Mac.
  CpuUsageSamplerToTest(Daemon::Utilities* utilities, CpuCache* cpu_cache)
      : CpuUsageSampler(utilities, cpu_cache) {
    ResetUsageFiles(std::unique_ptr<ProcfsFiles>(new MockProcfsFiles()));
  }
};

TEST(CpuUsageSamplerTest, SampleOneApp) {
  const int32_t kMockAppPid = 100;

  // The following values are calculated manually from test files.
  const int64_t kAppCpuTime = 13780;
  const int64_t kSystemCpuTime = 25299780;
  const int64_t kElapsedTime = 1175801430;

  Daemon::Utilities utilities("");
  CpuCache cache{100};
  cache.AllocateAppCache(kMockAppPid);
  CpuUsageSamplerToTest sampler{&utilities, &cache};
  sampler.AddProcess(kMockAppPid);
  bool sample_result = sampler.Sample();
  ASSERT_TRUE(sample_result);

  // Test CPU usage data is properly sampled and cached.
  auto samples = cache.Retrieve(kMockAppPid, INT64_MIN, INT64_MAX);
  ASSERT_EQ(1, samples.size());
  auto sample = samples[0];
  EXPECT_EQ(kMockAppPid, sample.basic_info().process_id());
  EXPECT_LT(0, sample.basic_info().end_timestamp());
  EXPECT_EQ(kAppCpuTime, sample.cpu_usage().app_cpu_time_in_millisec());
  EXPECT_EQ(kSystemCpuTime, sample.cpu_usage().system_cpu_time_in_millisec());
  EXPECT_EQ(kElapsedTime, sample.cpu_usage().elapsed_time_in_millisec());
}

TEST(CpuUsageSamplerTest, SampleTwoApps) {
  const int32_t kMockAppPid_1 = 100;
  const int32_t kMockAppPid_2 = 101;

  // The following values are calculated manually from test files.
  const int64_t kAppCpuTime_1 = 13780;
  const int64_t kAppCpuTime_2 = 140;

  Daemon::Utilities utilities("");
  CpuCache cache{100};
  cache.AllocateAppCache(kMockAppPid_1);
  cache.AllocateAppCache(kMockAppPid_2);
  CpuUsageSamplerToTest sampler{&utilities, &cache};
  sampler.AddProcess(kMockAppPid_1);
  sampler.AddProcess(kMockAppPid_2);
  bool sample_result = sampler.Sample();
  ASSERT_TRUE(sample_result);

  // Test data for process 1 is sampled and cached.
  auto samples = cache.Retrieve(kMockAppPid_1, INT64_MIN, INT64_MAX);
  ASSERT_EQ(1, samples.size());
  auto sample = samples[0];
  EXPECT_EQ(kMockAppPid_1, sample.basic_info().process_id());
  EXPECT_EQ(kAppCpuTime_1, sample.cpu_usage().app_cpu_time_in_millisec());

  // Test data for process 2 is sampled and cached.
  samples = cache.Retrieve(kMockAppPid_2, INT64_MIN, INT64_MAX);
  ASSERT_EQ(1, samples.size());
  sample = samples[0];
  EXPECT_EQ(kMockAppPid_2, sample.basic_info().process_id());
  EXPECT_EQ(kAppCpuTime_2, sample.cpu_usage().app_cpu_time_in_millisec());

  // TODO: Enable the following test after cache supports proto::AppId::ANY.
  // Test the ANY_APP feature of the cache.
  // samples =
  //     cache.Retrieve(proto::CpuDataRequest::ANY_APP, INT64_MIN, INT64_MAX);
  // ASSERT_EQ(2, samples.size());
}

}  // namespace profiler
