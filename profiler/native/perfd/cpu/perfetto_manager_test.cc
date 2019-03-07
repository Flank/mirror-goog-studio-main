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
#include "perfetto_manager.h"
#include "fake_perfetto.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

using std::string;
namespace profiler {

TEST(PerfettoManagerTest, ProfilingStartStop) {
  std::shared_ptr<Perfetto> perfetto(new FakePerfetto());
  PerfettoManager manager{perfetto};
  perfetto::protos::TraceConfig config;
  string trace_path;
  string error;
  EXPECT_TRUE(manager.StartProfiling(config, &trace_path, &error));
  EXPECT_EQ(trace_path, PerfettoManager::kPerfettoTraceFile);
  EXPECT_TRUE(perfetto->IsPerfettoRunning());
  EXPECT_TRUE(manager.StopProfiling(&error));
  EXPECT_FALSE(perfetto->IsPerfettoRunning());
}

}  // namespace profiler