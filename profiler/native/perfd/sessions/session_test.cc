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
#include "session.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "daemon/daemon.h"
#include "daemon/event_buffer.h"
#include "perfd/samplers/sampler.h"
#include "perfd/sessions/session.h"
#include "perfd/statsd/pulled_atoms/wifi_bytes_transfer.h"
#include "perfd/statsd/statsd_subscriber.h"
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/device_info.h"
#include "utils/device_info_helper.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

namespace profiler {

namespace {
class MockSampler final : public Sampler {
 public:
  MockSampler(const profiler::Session& session, EventBuffer* buffer,
              int64_t sample_interval_ms)
      : Sampler(session, buffer, sample_interval_ms) {}

  // Recommended approach to mock destructors from
  // https://github.com/abseil/googletest/blob/master/googlemock/docs/CookBook.md#mocking-destructors
  MOCK_METHOD0(Die, void());
  virtual ~MockSampler() { Die(); }
};

}  // namespace

TEST(Session, SamplersAddedForNewPipeline) {
  FakeClock clock;
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  proto::AgentConfig agent_config;
  Config config1(agent_config);
  Daemon daemon1(&clock, &config1, &file_cache, &event_buffer);
  Session session1(0, 0, 0, &daemon1);
  EXPECT_EQ(session1.samplers().size(), 0);

  agent_config.set_profiler_unified_pipeline(true);
  Config config2(agent_config);
  Daemon daemon2(&clock, &config2, &file_cache, &event_buffer);
  Session session2(0, 0, 0, &daemon2);
  EXPECT_GT(session2.samplers().size(), 0);
}

TEST(Session, SamplerDeallocatedWhenSessionDies) {
  FakeClock clock;
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  proto::AgentConfig agent_config;
  Config config1(agent_config);
  Daemon daemon1(&clock, &config1, &file_cache, &event_buffer);
  Session session1(0, 0, 0, &daemon1);
  EXPECT_EQ(session1.samplers().size(), 0);

  agent_config.set_profiler_unified_pipeline(true);
  Config config2(agent_config);
  Daemon daemon2(&clock, &config2, &file_cache, &event_buffer);
  Session session2(0, 0, 0, &daemon2);
  EXPECT_GT(session2.samplers().size(), 0);

  // Create a new instance of sampler that's mocked to monitor the destructor.
  auto* sampler = new MockSampler(session2, &event_buffer, 1000);
  // The test will fail if commenting the following line with reset().
  session2.samplers()[0].reset(sampler);
  // When session2 is out of scope, its samplers are expected to be deallocated.
  EXPECT_CALL(*sampler, Die());
}

TEST(Session, UsesStatsdForQ) {
  FakeClock clock;
  EventBuffer event_buffer(&clock);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  proto::AgentConfig agent_config;
  Config config(agent_config);
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);

  DeviceInfoHelper::SetDeviceInfo(DeviceInfo::P);
  Session session1(0, 1, 0, &daemon);
  EXPECT_EQ(nullptr,
            StatsdSubscriber::Instance().FindAtom<WifiBytesTransfer>(10000));

  DeviceInfoHelper::SetDeviceInfo(DeviceInfo::Q);
  Session session2(0, 1, 0, &daemon);
  EXPECT_NE(nullptr,
            StatsdSubscriber::Instance().FindAtom<WifiBytesTransfer>(10000));
}

}  // namespace profiler
