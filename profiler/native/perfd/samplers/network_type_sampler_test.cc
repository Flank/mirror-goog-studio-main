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
#include "network_type_sampler.h"

#include <gtest/gtest.h>
#include <memory>

#include "daemon/daemon.h"
#include "perfd/network/fake_network_type_provider.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

using profiler::proto::Event;
using profiler::proto::NetworkTypeData;

namespace profiler {

TEST(NetworkTypeSamplerTest, NetworkTypeFromProvider) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  int32_t pid = 1;
  Session session(0, pid, 0, &daemon);
  auto network_type_mobile =
      std::make_shared<FakeNetworkTypeProvider>(NetworkTypeData::MOBILE);
  NetworkTypeSampler sampler(session, &event_buffer, network_type_mobile);

  sampler.Sample();
  auto groups = event_buffer.Get(Event::NETWORK_TYPE, 0, LLONG_MAX);
  ASSERT_EQ(1, groups.size());
  ASSERT_EQ(1, groups[0].events().size());
  auto event = groups[0].events(0);
  EXPECT_TRUE(event.has_network_type());
  EXPECT_EQ(NetworkTypeData::MOBILE, event.network_type().network_type());
}

}  // namespace profiler