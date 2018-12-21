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
#include "memory_usage_sampler.h"

#include <gtest/gtest.h>

#include "daemon/daemon.h"
#include "perfd/memory/memory_usage_reader.h"
#include "perfd/sessions/session.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

namespace profiler {

class FakeMemoryUsageReader : public MemoryUsageReader {
 public:
  ~FakeMemoryUsageReader() override = default;

  void GetProcessMemoryLevels(int pid,
                              proto::MemoryUsageData* sample) override {
    sample->set_java_mem(1);
    sample->set_native_mem(2);
    sample->set_stack_mem(3);
    sample->set_graphics_mem(4);
    sample->set_code_mem(5);
    sample->set_others_mem(6);
    sample->set_total_mem(7);
    return;
  }
};

TEST(MemoryUsageSampler, TestSampleMemoryUsage) {
  FakeClock clock;
  proto::AgentConfig agent_config;
  Config config(agent_config);
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  Session session(0, 0, 0, &daemon);
  MemoryUsageSampler sampler(session, &clock, &event_buffer,
                             new FakeMemoryUsageReader());
  sampler.Sample();

  proto::EventGroup group;
  ASSERT_TRUE(event_buffer.GetGroup(0, &group));
  ASSERT_EQ(1, group.events_size());
  ASSERT_TRUE(group.events(0).has_memory_usage());
  auto data = group.events(0).memory_usage();
  ASSERT_EQ(1, data.java_mem());
  ASSERT_EQ(2, data.native_mem());
  ASSERT_EQ(3, data.stack_mem());
  ASSERT_EQ(4, data.graphics_mem());
  ASSERT_EQ(5, data.code_mem());
  ASSERT_EQ(6, data.others_mem());
  ASSERT_EQ(7, data.total_mem());
}

}  // namespace profiler