/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "atrace_manager.h"
#include "fake_atrace.h"
#include "utils/current_process.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"
#include "utils/tokenizer.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <condition_variable>
#include <queue>

using profiler::proto::CpuProfilingAppStopResponse;
using std::string;
using testing::EndsWith;
using testing::Eq;

namespace profiler {

// Simple helper struct to define test data used across multiple test.
struct TestInitializer {
 public:
  TestInitializer()
      : fake_clock(0),
        atrace(new FakeAtrace(&fake_clock)),
        app_name("Fake_App") {}
  FakeClock fake_clock;
  FakeAtrace* atrace;
  std::string app_name;
  std::string trace_path;
  std::string error;
};

void EnqueueExpectedParams(TestInitializer& test_data, AtraceManager& manager,
                           const std::string& path_append,
                           const std::string& cmd, const std::string& buffer,
                           bool running) {
  AtraceArgs start_args{
      test_data.app_name,
      manager.GetTracePath(test_data.app_name).append(path_append), cmd,
      buffer};
  test_data.atrace->EnqueueExpectedParams({start_args, running});
}

TEST(AtraceManagerTest, ProfilingStartStop) {
  TestInitializer test_data;
  int allocated_buffer_size_kb = 0;
  AtraceManager manager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_dump", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "1", "--async_dump", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "2", "--async_stop", "", false);
  int dump_count = 2;
  EXPECT_TRUE(manager.StartProfiling(test_data.app_name, 1000, 8,
                                     &allocated_buffer_size_kb,
                                     &test_data.trace_path, &test_data.error));
  EXPECT_TRUE(manager.IsProfiling());
  test_data.atrace->WaitUntilParamsSize(1);
  EXPECT_EQ(manager.GetDumpCount(), dump_count);
  EXPECT_EQ(CpuProfilingAppStopResponse::SUCCESS,
            manager.StopProfiling(test_data.app_name, false, &test_data.error));
  EXPECT_FALSE(manager.IsProfiling());
}

TEST(AtraceManagerTest, ProfilerReentrant) {
  TestInitializer test_data;
  int allocated_buffer_size_kb = 0;
  AtraceManager manager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  int retry_count = 3;
  int dump_count = 2;
  for (int i = 0; i < retry_count; i++) {
    EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                          true);
    EnqueueExpectedParams(test_data, manager, "0", "--async_dump", "-b 8192",
                          true);
    EnqueueExpectedParams(test_data, manager, "1", "--async_dump", "-b 8192",
                          true);
    EnqueueExpectedParams(test_data, manager, "2", "--async_stop", "", false);
    EXPECT_TRUE(manager.StartProfiling(
        test_data.app_name, 1000, 8, &allocated_buffer_size_kb,
        &test_data.trace_path, &test_data.error));
    EXPECT_TRUE(manager.IsProfiling());
    test_data.atrace->WaitUntilParamsSize(1);
    EXPECT_EQ(manager.GetDumpCount(), dump_count);
    EXPECT_EQ(
        CpuProfilingAppStopResponse::SUCCESS,
        manager.StopProfiling(test_data.app_name, false, &test_data.error));
    EXPECT_FALSE(manager.IsProfiling());
  }
}

TEST(AtraceManagerTest, ProfilingStartTwice) {
  TestInitializer test_data;
  int allocated_buffer_size_kb = 0;
  AtraceManager manager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_dump", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "1", "--async_stop", "", false);
  EXPECT_TRUE(manager.StartProfiling(test_data.app_name, 1000, 8,
                                     &allocated_buffer_size_kb,
                                     &test_data.trace_path, &test_data.error));
  test_data.atrace->WaitUntilParamsSize(1);
  EXPECT_EQ(manager.GetDumpCount(), 1);
  EXPECT_TRUE(manager.IsProfiling());
  EXPECT_FALSE(manager.StartProfiling(test_data.app_name, 1000, 8,
                                      &allocated_buffer_size_kb,
                                      &test_data.trace_path, &test_data.error));
  EXPECT_EQ(manager.GetDumpCount(), 1);
  EXPECT_EQ(CpuProfilingAppStopResponse::SUCCESS,
            manager.StopProfiling(test_data.app_name, false, &test_data.error));
}

TEST(AtraceManagerTest, StartStopFailsAndReturnsError) {
  TestInitializer test_data;
  int allocated_buffer_size_kb = 0;
  AtraceManager manager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  // Async start fails, so the manager retries kRetryStartTimes times.
  for (int i = 0; i < AtraceManager::kRetryStartAttempts; i++) {
    EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                          false);
  }
  // Actually start the profiling
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                        true);
  // Async stop fails so the manager retries 5 times.
  EnqueueExpectedParams(test_data, manager, "0", "--async_stop", "", true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_stop", "", true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_stop", "", true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_stop", "", true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_stop", "", true);
  EXPECT_FALSE(manager.StartProfiling(test_data.app_name, 1000, 8,
                                      &allocated_buffer_size_kb,
                                      &test_data.trace_path, &test_data.error));
  EXPECT_THAT(test_data.error, Eq("Failed to run atrace start."));
  test_data.error.clear();
  EXPECT_TRUE(manager.StartProfiling(test_data.app_name, 1000, 8,
                                     &allocated_buffer_size_kb,
                                     &test_data.trace_path, &test_data.error));
  EXPECT_EQ(CpuProfilingAppStopResponse::STILL_PROFILING_AFTER_STOP,
            manager.StopProfiling(test_data.app_name, false, &test_data.error));
  EXPECT_THAT(test_data.error, Eq("Failed to stop atrace."));
}

TEST(AtraceManagerTest, BufferAutoDownSamples) {
  TestInitializer test_data;
  test_data.atrace->SetBufferSize(4096);
  int allocated_buffer_size_kb = 0;
  AtraceManager manager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  // Test buffer size gets reduced by some amount.
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 24576",
                        true);
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 16384",
                        true);
  // Test we hit a min threashold and we divide the buffer in half.
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 4096",
                        true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_dump", "-b 4096",
                        true);
  EnqueueExpectedParams(test_data, manager, "1", "--async_stop", "", false);
  EXPECT_TRUE(manager.StartProfiling(test_data.app_name, 1000, 24,
                                     &allocated_buffer_size_kb,
                                     &test_data.trace_path, &test_data.error));
  EXPECT_EQ(allocated_buffer_size_kb, 4096);
  EXPECT_TRUE(manager.IsProfiling());
  EXPECT_EQ(manager.GetDumpCount(), 0);
  test_data.atrace->WaitUntilParamsSize(1);
  EXPECT_EQ(manager.GetDumpCount(), 1);
  EXPECT_EQ(CpuProfilingAppStopResponse::SUCCESS,
            manager.StopProfiling(test_data.app_name, false, &test_data.error));
}

TEST(AtraceManagerTest, StopProfilingCombinesFiles) {
  TestInitializer test_data;
  int allocated_buffer_size_kb = 0;
  // Ownership of the file system is passed to atrace manager, but the test
  // still needs access to it. The access is used to create fake dump files, as
  // well as validate the output file.
  MemoryFileSystem* file_system = new MemoryFileSystem();
  AtraceManager manager(std::unique_ptr<FileSystem>(file_system),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  char index_buffer[10];

  for (int i = 0; i < 3; i++) {
    sprintf(index_buffer, "%d", i);
    std::shared_ptr<File> file = file_system->GetOrNewFile(
        manager.GetTracePath(test_data.app_name).append(index_buffer));
    file->OpenForWrite();
    file->Append(index_buffer);
    file->Close();
  }
  // Async start fails, so the manager retries
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "0", "--async_dump", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "1", "--async_dump", "-b 8192",
                        true);
  EnqueueExpectedParams(test_data, manager, "2", "--async_stop", "", false);
  EXPECT_TRUE(manager.StartProfiling(test_data.app_name, 1000, 8,
                                     &allocated_buffer_size_kb,
                                     &test_data.trace_path, &test_data.error));
  EXPECT_EQ(allocated_buffer_size_kb, 8192);
  EXPECT_TRUE(manager.IsProfiling());
  test_data.atrace->WaitUntilParamsSize(1);
  EXPECT_EQ(CpuProfilingAppStopResponse::SUCCESS,
            manager.StopProfiling(test_data.app_name, true, &test_data.error));

  // On stop profiling get the dump count (this is incremented by stop
  // profiling)
  EXPECT_EQ(manager.GetDumpCount(), 3);
  // Read files from the memory file system.
  std::string contents = file_system->GetFileContents(test_data.trace_path);
  EXPECT_EQ(contents, "012");
}

TEST(AtraceManagerTest, BufferSizeTooSmallReturnsError) {
  TestInitializer test_data;
  test_data.atrace->SetBufferSize(2048);
  int allocated_buffer_size_kb = 0;
  AtraceManager manager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                        &test_data.fake_clock, 200,
                        std::unique_ptr<Atrace>(test_data.atrace));
  // Async start fails, so the manager retries
  EnqueueExpectedParams(test_data, manager, "", "--async_start", "-b 0", true);
  EXPECT_FALSE(manager.StartProfiling(test_data.app_name, 1000, 0,
                                      &allocated_buffer_size_kb,
                                      &test_data.trace_path, &test_data.error));
  EXPECT_THAT(test_data.error, Eq("Requested buffer size is too small"));
}
}  // namespace profiler
