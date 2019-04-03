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
#include "network_collector.h"

#include <gtest/gtest.h>

#include "utils/daemon_config.h"
#include "utils/fake_clock.h"

namespace profiler {

TEST(NetworkCollector, SamplersEmptyForNewPipeline) {
  FakeClock clock;
  proto::DaemonConfig daemon_config;
  DaemonConfig config1(daemon_config);
  NetworkCollector collector1(config1, &clock, 1);
  EXPECT_EQ(collector1.samplers().size(), 3);

  daemon_config.mutable_common()->set_profiler_unified_pipeline(true);
  DaemonConfig config2(daemon_config);
  NetworkCollector collector2(config2, &clock, 1);
  EXPECT_EQ(collector2.samplers().size(), 0);
}

}  // namespace profiler