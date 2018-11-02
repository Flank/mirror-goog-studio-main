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

#include <gtest/gtest.h>

#include "perfd/daemon.h"
#include "perfd/event_buffer.h"
#include "perfd/sessions/session.h"
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

namespace profiler {

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

  agent_config.set_unified_pipeline(true);
  Config config2(agent_config);
  Daemon daemon2(&clock, &config2, &file_cache, &event_buffer);
  Session session2(0, 0, 0, &daemon2);
  EXPECT_GT(session2.samplers().size(), 0);
}

}  // namespace profiler