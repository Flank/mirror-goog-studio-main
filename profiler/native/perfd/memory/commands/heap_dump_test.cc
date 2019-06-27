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
#include "heap_dump.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "daemon/event_writer.h"
#include "perfd/memory/heap_dump_manager.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

using std::string;

using ::testing::_;
using ::testing::Return;

using profiler::proto::HeapDumpStatus;

namespace profiler {
class MockAcitivtyManager final : public ActivityManager {
 public:
  explicit MockAcitivtyManager()
      : ActivityManager(
            std::unique_ptr<BashCommandRunner>(new BashCommandRunner("blah"))) {
  }
  MOCK_CONST_METHOD3(TriggerHeapDump,
                     bool(int pid, const std::string& file_path,
                          std::string* error_string));
};

// Helper class to handle even streaming from the EventBuffer.
class TestEventWriter final : public EventWriter {
 public:
  TestEventWriter(std::vector<proto::Event>* events,
                  std::condition_variable* cv)
      : events_(events), cv_(cv) {}

  bool Write(const proto::Event& event) override {
    events_->push_back(event);
    cv_->notify_one();
    return true;
  }

 private:
  std::vector<proto::Event>* events_;
  std::condition_variable* cv_;
};

// Test that we receive the start and end events for a successful heap dump.
TEST(HeapDumpTest, CommandsGeneratesEvents) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);

  MockAcitivtyManager activity_manager;
  EXPECT_CALL(activity_manager, TriggerHeapDump(_, _, _))
      .WillRepeatedly(Return(true));
  HeapDumpManager dump(&file_cache, &activity_manager);

  // Start the event writer to listen for incoming events on a separate
  // thread.
  std::condition_variable cv;
  std::vector<proto::Event> events;
  TestEventWriter writer(&events, &cv);
  std::thread read_thread(
      [&writer, &event_buffer] { event_buffer.WriteEventsTo(&writer); });

  // Execute the start command
  clock.SetCurrentTime(10);
  proto::Command command;
  command.set_type(proto::Command::HEAP_DUMP);
  HeapDump::Create(command, &dump)->ExecuteOn(&daemon);
  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a status, start and end event
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 3; }));
  }

  EXPECT_EQ(3, events.size());
  EXPECT_EQ(events[0].kind(), proto::Event::MEMORY_HEAP_DUMP_STATUS);
  EXPECT_TRUE(events[0].has_memory_heapdump_status());
  EXPECT_EQ(events[0].memory_heapdump_status().status().status(),
            HeapDumpStatus::SUCCESS);
  EXPECT_EQ(events[0].memory_heapdump_status().status().start_time(), 10);

  EXPECT_EQ(events[1].kind(), proto::Event::MEMORY_HEAP_DUMP);
  EXPECT_TRUE(events[1].has_memory_heapdump());
  EXPECT_EQ(events[1].memory_heapdump().info().start_time(), 10);
  EXPECT_EQ(events[1].memory_heapdump().info().end_time(), LLONG_MAX);
  EXPECT_FALSE(events[1].memory_heapdump().info().success());

  EXPECT_EQ(events[2].kind(), proto::Event::MEMORY_HEAP_DUMP);
  EXPECT_TRUE(events[2].has_memory_heapdump());
  EXPECT_EQ(events[2].memory_heapdump().info().start_time(), 10);
  EXPECT_EQ(events[2].memory_heapdump().info().end_time(), 10);
  // TODO success status from the FileSystemNotifier apis seems
  // platform-dependent in our test scenario. Refactor the logic in
  // heap_dump_manager_test so we can test the O+ workflow here instead.

  // Kill read thread to cleanly exit test.
  event_buffer.InterruptWriteEvents();
  read_thread.join();
}
}  // namespace profiler
