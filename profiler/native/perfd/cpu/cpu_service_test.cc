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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include "perfd/cpu/fake_atrace.h"
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

using std::string;
using testing::HasSubstr;
using testing::Return;
using testing::SaveArg;
using testing::StartsWith;

namespace profiler {

namespace {
const char* const kAmExecutable = "/aaaaa/system/bin/am";
const char* const kProfileStart = "profile start";
const char* const kProfileStop = "profile stop";
}  // namespace

// A subclass of ActivityManager that we want to test. The only difference is it
// has a public constructor.
class TestActivityManager final : public ActivityManager {
 public:
  explicit TestActivityManager(std::unique_ptr<BashCommandRunner> bash)
      : ActivityManager(std::move(bash)) {}
};

// A mock BashCommandRunner that mocks the execution of command.
// We need the mock to run tests across platforms to examine the commands
// generated by ActivityManager.
class MockBashCommandRunner final : public BashCommandRunner {
 public:
  explicit MockBashCommandRunner(const std::string& executable_path)
      : BashCommandRunner(executable_path) {}
  MOCK_CONST_METHOD2(RunAndReadOutput,
                     bool(const std::string& cmd, std::string* output));
};

// A subclass of TerminationService that we want to test. The only difference is
// it has a public constructor and destructor.
class TestTerminationService final : public TerminationService {
 public:
  explicit TestTerminationService() = default;
  ~TestTerminationService() = default;
};

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
  std::unique_ptr<TestTerminationService> termination_service{
      new TestTerminationService()};
  CpuServiceImpl cpu_service{
      &clock,
      &cache,
      &sampler,
      &thread_monitor,
      cpu_config,
      termination_service.get(),
      ActivityManager::Instance(),
      std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
          &clock, std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
      std::unique_ptr<AtraceManager>(new AtraceManager(
          std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock, 50,
          std::unique_ptr<Atrace>(new FakeAtrace(&clock))))};

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
  termination_service.reset(nullptr);
  // Now, verify that command to kill simpleperf has been issued.
  EXPECT_TRUE(fake_simpleperf->GetKillSimpleperfCalled());
}

TEST(CpuServiceTest, StopArtTraceWhenPerfdTerminated) {
  const int64_t kSessionId = 123;
  const int32_t kPid = 456;

  // Set up test Activity Manager
  string trace_path;
  string output_string;
  string cmd_1, cmd_2;
  std::unique_ptr<BashCommandRunner> bash{
      new MockBashCommandRunner(kAmExecutable)};
  EXPECT_CALL(
      *(static_cast<MockBashCommandRunner*>(bash.get())),
      RunAndReadOutput(testing::A<const string&>(), testing::A<string*>()))
      .Times(2)
      .WillOnce(DoAll(SaveArg<0>(&cmd_1), Return(true)))
      .WillOnce(DoAll(SaveArg<0>(&cmd_2), Return(true)));

  TestActivityManager activity_manager{std::move(bash)};

  // Set up CPU service.
  FakeClock clock;
  FileCache file_cache(
      std::unique_ptr<profiler::FileSystem>(new profiler::MemoryFileSystem()),
      "/");
  CpuCache cache{100, &clock, &file_cache};
  CpuUsageSampler sampler{&clock, &cache};
  ThreadMonitor thread_monitor{&clock, &cache};
  profiler::proto::AgentConfig::CpuConfig cpu_config;
  std::unique_ptr<TestTerminationService> termination_service{
      new TestTerminationService()};
  CpuServiceImpl cpu_service{
      &clock,
      &cache,
      &sampler,
      &thread_monitor,
      cpu_config,
      termination_service.get(),
      &activity_manager,
      std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
          &clock, std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
      std::unique_ptr<AtraceManager>(new AtraceManager(
          std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock, 50,
          std::unique_ptr<Atrace>(new FakeAtrace(&clock))))};

  // Start an ART recording.
  ServerContext context;
  CpuProfilingAppStartRequest start_request;
  start_request.mutable_session()->set_session_id(kSessionId);
  start_request.mutable_session()->set_pid(kPid);
  start_request.mutable_configuration()->set_profiler_mode(
      CpuProfilerMode::SAMPLED);
  start_request.mutable_configuration()->set_profiler_type(
      CpuProfilerType::ART);
  CpuProfilingAppStartResponse start_response;
  cpu_service.StartProfilingApp(&context, &start_request, &start_response);
  EXPECT_THAT(cmd_1, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd_1, HasSubstr(kProfileStart));

  // Simulate that perfd is killed.
  termination_service.reset(nullptr);
  // Now, verify that a command has been issued to stop ART recording.
  EXPECT_THAT(cmd_2, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd_2, HasSubstr(kProfileStop));
}

}  // namespace profiler
