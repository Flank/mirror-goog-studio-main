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
#include "cpu_thread_sampler.h"

#include <gtest/gtest.h>
#include <sstream>

#include "perfd/daemon.h"
#include "perfd/sessions/session.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "test/utils.h"
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"
#include "utils/procfs_files.h"

using profiler::ProcfsFiles;
using profiler::TestUtils;
using profiler::proto::CpuThreadData;
using profiler::proto::Event;
using profiler::proto::EventGroup;
using std::ostringstream;
using std::string;
using std::unique_ptr;

namespace {

// A test-use-only class that uses checked-in files as test data to mock
// thread-related files. It uses folders named "t<timestamp>" to mimic time
// lapse.
class MockProcfsFiles final : public ProcfsFiles {
 public:
  MockProcfsFiles(profiler::Clock* clock) : ProcfsFiles(), clock_(clock) {}

  string GetProcessTaskDir(int32_t pid) const override {
    ostringstream os;
    os << "pid_task_" << pid << "/t" << clock_->GetCurrentTime() << "/";
    return TestUtils::getCpuTestData(os.str());
  }

  string GetThreadStatFilePath(int32_t pid, int32_t tid) const override {
    ostringstream os;
    os << "pid_task_" << pid << "/t" << clock_->GetCurrentTime() << "/" << tid
       << "/stat";
    return TestUtils::getCpuTestData(os.str());
  }

 private:
  profiler::Clock* clock_;
};
}  // namespace

namespace profiler {

TEST(CpuThreadSamplerTest, SampleCpuThreads) {
  FakeClock clock;
  proto::AgentConfig agent_config;
  Config config(agent_config);
  FileCache file_cache(unique_ptr<FileSystem>(new MemoryFileSystem()), "/");
  EventBuffer event_buffer(&clock);
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  int32_t pid = 1;
  Session session(0, pid, 0, &daemon);
  CpuThreadSampler sampler(session, &clock, &event_buffer,
                           new MockProcfsFiles(&clock));

  // t0
  {
    sampler.Sample();
    size_t expected_thread_count = 3;
    int32_t thread_ids[] = {1, 2, 3};
    string thread_names[] = {"foo", "bar", "foobar"};
    CpuThreadData::State thread_states[] = {CpuThreadData::RUNNING,
                                            CpuThreadData::RUNNING,
                                            CpuThreadData::SLEEPING};

    auto groups = event_buffer.Get(session.info().session_id(),
                                   Event::CPU_THREAD, 0, LONG_MAX);
    ASSERT_EQ(expected_thread_count, groups.size());
    for (size_t i = 0; i < expected_thread_count; ++i) {
      EventGroup group;
      ASSERT_TRUE(event_buffer.GetGroup(thread_ids[i], &group));
      ASSERT_EQ(1, group.events().size());
      ASSERT_TRUE(group.events(0).has_cpu_thread());
      const auto& thread = group.events(0).cpu_thread();
      ASSERT_EQ(thread_ids[i], thread.tid());
      ASSERT_EQ(thread_names[i], thread.name());
      ASSERT_EQ(thread_states[i], thread.state());
    }
  }

  // t1
  {
    clock.Elapse(1);
    sampler.Sample();
    size_t expected_thread_count = 4;
    size_t expected_events_count[] = {1, 2, 2, 1};
    int32_t thread_ids[] = {1, 2, 3, 4};
    string thread_names[] = {"foo", "bar", "", "barfoo"};
    CpuThreadData::State thread_states[] = {
        CpuThreadData::RUNNING, CpuThreadData::SLEEPING, CpuThreadData::DEAD,
        CpuThreadData::RUNNING};

    auto groups = event_buffer.Get(session.info().session_id(),
                                   Event::CPU_THREAD, 0, LONG_MAX);
    ASSERT_EQ(expected_thread_count, groups.size());
    for (size_t i = 0; i < expected_thread_count; ++i) {
      EventGroup group;
      ASSERT_TRUE(event_buffer.GetGroup(thread_ids[i], &group));
      ASSERT_EQ(expected_events_count[i], group.events().size());
      // Only verify the last event.
      size_t last_index = expected_events_count[i] - 1;
      ASSERT_TRUE(group.events(last_index).has_cpu_thread());
      const auto& thread = group.events(last_index).cpu_thread();
      ASSERT_EQ(thread_ids[i], thread.tid());
      ASSERT_EQ(thread_names[i], thread.name());
      ASSERT_EQ(thread_states[i], thread.state());
    }
  }
}

}  // namespace profiler