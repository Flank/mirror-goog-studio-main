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
#include <algorithm>
#include <sstream>

#include "daemon/daemon.h"
#include "perfd/sessions/session.h"
#include "proto/agent_service.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "test/utils.h"
#include "utils/clock.h"
#include "utils/daemon_config.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/disk_file_system.h"
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
  MockProcfsFiles(profiler::Clock* clock, const string& working_dir)
      : ProcfsFiles(), clock_(clock), working_dir_(working_dir) {}

  string GetProcessTaskDir(int32_t pid) const override {
    ostringstream dir, abs_dir;
    dir << "pid_task_" << pid << "/t" << clock_->GetCurrentTime() << "/";
    // FileSystem#WalkDir uses absolute path.
    abs_dir << working_dir_ << "/" << TestUtils::getCpuTestData(dir.str());
    return abs_dir.str();
  }

  string GetThreadStatFilePath(int32_t pid, int32_t tid) const override {
    ostringstream os;
    os << "pid_task_" << pid << "/t" << clock_->GetCurrentTime() << "/" << tid
       << "/stat";
    return TestUtils::getCpuTestData(os.str());
  }

 private:
  profiler::Clock* clock_;
  const string working_dir_;
};
}  // namespace

namespace profiler {

bool cmp(const proto::EventGroup& first, const proto::EventGroup& second) {
  return first.group_id() < second.group_id();
}

TEST(CpuThreadSamplerTest, SampleCpuThreads) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(unique_ptr<FileSystem>(new MemoryFileSystem()), "/");
  EventBuffer event_buffer(&clock);
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  int32_t pid = 1;
  Session session(0, pid, 0, &daemon);
  DiskFileSystem fs;
  CpuThreadSampler sampler(session, &clock, &event_buffer,
                           new MockProcfsFiles(&clock, fs.GetWorkingDir()));

  // t=t0
  // query range: (0, +inf)
  {
    sampler.Sample();
    size_t expected_thread_count = 3;
    int32_t thread_ids[] = {1, 2, 3};
    string thread_names[] = {"foo", "bar", "foobar"};
    bool is_ended_states[] = {false, false, false};
    CpuThreadData::State thread_states[] = {CpuThreadData::RUNNING,
                                            CpuThreadData::RUNNING,
                                            CpuThreadData::SLEEPING};

    auto groups = event_buffer.Get(Event::CPU_THREAD, 0, LONG_MAX);
    ASSERT_EQ(expected_thread_count, groups.size());
    // Sort groups by group ID.
    std::sort(groups.begin(), groups.end(), cmp);
    for (size_t i = 0; i < expected_thread_count; ++i) {
      ASSERT_EQ(1, groups[i].events().size());
      ASSERT_EQ(is_ended_states[i], groups[i].events(0).is_ended());
      ASSERT_TRUE(groups[i].events(0).has_cpu_thread());
      const auto& thread = groups[i].events(0).cpu_thread();
      ASSERT_EQ(thread_ids[i], thread.tid());
      ASSERT_EQ(thread_names[i], thread.name());
      ASSERT_EQ(thread_states[i], thread.state());
    }
  }

  // t=t1
  // query range: (0, +inf)
  {
    clock.Elapse(1);
    sampler.Sample();
    size_t expected_thread_count = 4;
    size_t expected_events_count[] = {1, 2, 2, 1};
    int32_t thread_ids[] = {1, 2, 3, 4};
    string thread_names[] = {"foo", "bar", "foobar", "barfoo"};
    bool is_ended_states[] = {false, false, true, false};
    CpuThreadData::State thread_states[] = {
        CpuThreadData::RUNNING, CpuThreadData::SLEEPING, CpuThreadData::DEAD,
        CpuThreadData::RUNNING};

    auto groups = event_buffer.Get(Event::CPU_THREAD, 0, LONG_MAX);
    ASSERT_EQ(expected_thread_count, groups.size());
    // Sort groups by group ID.
    std::sort(groups.begin(), groups.end(), cmp);
    for (size_t i = 0; i < expected_thread_count; ++i) {
      ASSERT_EQ(expected_events_count[i], groups[i].events().size());
      // Only verify the last event.
      size_t last_index = expected_events_count[i] - 1;
      ASSERT_EQ(is_ended_states[i], groups[i].events(last_index).is_ended());
      ASSERT_TRUE(groups[i].events(last_index).has_cpu_thread());
      const auto& thread = groups[i].events(last_index).cpu_thread();
      ASSERT_EQ(thread_ids[i], thread.tid());
      ASSERT_EQ(thread_names[i], thread.name());
      ASSERT_EQ(thread_states[i], thread.state());
    }
  }

  // t=t2
  // query range: (t2, +inf)
  {
    // No new data
    size_t expected_thread_count = 3;
    size_t expected_events_count[] = {1, 2, 1};
    int32_t thread_ids[] = {1, 2, 4};
    string thread_names[] = {"foo", "bar", "barfoo"};
    bool is_ended_states[] = {false, false, false};
    CpuThreadData::State thread_states[] = {CpuThreadData::RUNNING,
                                            CpuThreadData::SLEEPING,
                                            CpuThreadData::RUNNING};

    auto groups = event_buffer.Get(Event::CPU_THREAD, 2, LONG_MAX);
    ASSERT_EQ(expected_thread_count, groups.size());
    // Sort groups by group ID.
    std::sort(groups.begin(), groups.end(), cmp);
    for (size_t i = 0; i < expected_thread_count; ++i) {
      ASSERT_EQ(expected_events_count[i], groups[i].events().size());
      // Only verify the last event.
      size_t last_index = expected_events_count[i] - 1;
      ASSERT_EQ(is_ended_states[i], groups[i].events(last_index).is_ended());
      ASSERT_TRUE(groups[i].events(last_index).has_cpu_thread());
      const auto& thread = groups[i].events(last_index).cpu_thread();
      ASSERT_EQ(thread_ids[i], thread.tid());
      ASSERT_EQ(thread_names[i], thread.name());
      ASSERT_EQ(thread_states[i], thread.state());
    }
  }
}

}  // namespace profiler