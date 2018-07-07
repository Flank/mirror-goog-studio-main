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
#include "perfd/cpu/cpu_service.h"

#include <gtest/gtest.h>
#include "perfd/cpu/fake_atrace_manager.h"
#include "perfd/cpu/fake_simpleperf.h"
#include "perfd/termination_service.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::CpuProfilerMode;
using profiler::proto::CpuProfilerType;
using profiler::proto::CpuProfilingAppStartRequest;
using profiler::proto::CpuProfilingAppStartResponse;

namespace profiler {

TEST(CpuServiceTest, StopSimpleperfTraceWhenPerfdTerminated) {
  const int64_t kSessionId = 123;
  const int32_t kPid = 456;

  // Set up CPU service.
  FakeClock clock;
  FileCache file_cache(
      std::unique_ptr<profiler::FileSystem>(new profiler::MemoryFileSystem()),
      "/");
  CpuCache cache{100, &clock, &file_cache};
  CpuUsageSampler sampler{&clock, &cache};
  ThreadMonitor thread_monitor{&clock, &cache};
  profiler::proto::AgentConfig::CpuConfig cpu_config;
  auto* termination_service = TerminationService::GetTerminationService();
  CpuServiceImpl cpu_service{
      &clock,
      &cache,
      &sampler,
      &thread_monitor,
      cpu_config,
      termination_service,
      ActivityManager::Instance(),
      std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
          &clock, std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
      std::unique_ptr<AtraceManager>(new FakeAtraceManager(&clock))};

  // Start a Simpleperf recording.
  ServerContext context;
  CpuProfilingAppStartRequest start_request;
  start_request.mutable_session()->set_session_id(kSessionId);
  start_request.mutable_session()->set_pid(kPid);
  start_request.mutable_configuration()->set_profiler_mode(
      CpuProfilerMode::SAMPLED);
  start_request.mutable_configuration()->set_profiler_type(
      CpuProfilerType::SIMPLEPERF);
  CpuProfilingAppStartResponse start_response;
  cpu_service.StartProfilingApp(&context, &start_request, &start_response);

  // Now, verify that no command has been issued to kill simpleperf.
  auto* fake_simpleperf = dynamic_cast<FakeSimpleperf*>(
      cpu_service.simpleperf_manager()->simpleperf());
  EXPECT_FALSE(fake_simpleperf->GetKillSimpleperfCalled());
  // Simulate that perfd is killed.
  delete termination_service;
  // Now, verify that command to kill simpleperf has been issued.
  EXPECT_TRUE(fake_simpleperf->GetKillSimpleperfCalled());
}

}  // namespace profiler
