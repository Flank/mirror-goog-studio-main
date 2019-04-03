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
#include "cpu_usage_sampler.h"

#include <gtest/gtest.h>

#include "daemon/daemon.h"
#include "perfd/cpu/cpu_usage_sampler.h"
#include "perfd/sessions/session.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/clock.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

namespace profiler {

class FakeCpuUsageSampler : public CpuUsageSampler {
 public:
  FakeCpuUsageSampler(Clock* clock) : CpuUsageSampler(clock, nullptr) {}

  bool SampleAProcess(int32_t pid, proto::CpuUsageData* data) override {
    data->set_system_cpu_time_in_millisec(1000);
    data->set_elapsed_time_in_millisec(2000);
    data->set_app_cpu_time_in_millisec(500);

    auto* core = data->add_cores();
    core->set_core(0);
    core->set_system_cpu_time_in_millisec(500);
    core->set_elapsed_time_in_millisec(1000);
    return true;
  }
};

TEST(CpuUsageDataSampler, TestSampleCpuUsage) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  int32_t pid = 321;
  Session session(0, pid, 0, &daemon);
  CpuUsageDataSampler sampler(session, &event_buffer,
                              new FakeCpuUsageSampler(&clock));

  sampler.Sample();

  proto::EventGroup group;
  ASSERT_TRUE(event_buffer.GetGroup(pid, &group));
  ASSERT_EQ(1, group.events_size());
  ASSERT_TRUE(group.events(0).has_cpu_usage());
  auto data = group.events(0).cpu_usage();
  ASSERT_EQ(1000, data.system_cpu_time_in_millisec());
  ASSERT_EQ(2000, data.elapsed_time_in_millisec());
  ASSERT_EQ(500, data.app_cpu_time_in_millisec());
  ASSERT_EQ(1, data.cores_size());
  auto core_data = data.cores(0);
  ASSERT_EQ(0, core_data.core());
  ASSERT_EQ(500, core_data.system_cpu_time_in_millisec());
  ASSERT_EQ(1000, core_data.elapsed_time_in_millisec());
}

}  // namespace profiler