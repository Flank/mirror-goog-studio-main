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
#include "sessions_manager.h"

#include <gtest/gtest.h>
#include "daemon/event_writer.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"
#include "utils/process_manager.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

namespace profiler {

struct SessionsManagerTest : testing::Test {};

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

TEST_F(SessionsManagerTest, BeginSessionSendsQueuedEvents) {
  FakeClock clock;
  proto::DaemonConfig daemon_config;
  daemon_config.mutable_common()->set_profiler_unified_pipeline(true);
  DaemonConfig config(daemon_config);
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);

  Daemon daemon(&clock, &config, &file_cache, &event_buffer);

  auto* manager = SessionsManager::Instance();

  proto::Event event;
  event.set_kind(proto::Event::ECHO);
  auto* data = event.mutable_echo();
  data->set_data("test");

  std::string app_name(ProcessManager::GetCmdlineForPid(0));
  manager->SendOrQueueEventsForSession(&daemon, app_name, {event});

  // Start the event writer to listen for incoming events on a separate thread.
  std::condition_variable cv;
  std::vector<proto::Event> events;
  TestEventWriter writer(&events, &cv);
  std::thread read_thread(
      [&writer, &event_buffer] { event_buffer.WriteEventsTo(&writer); });

  proto::BeginSession begin_session;
  manager->BeginSession(&daemon, 0, 0, begin_session);

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the echo event before the timeout.
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 2; }));
  }
  EXPECT_TRUE(events.size() == 2);
  EXPECT_EQ(events[0].kind(), proto::Event::ECHO);
  EXPECT_TRUE(events[0].has_echo());
  EXPECT_EQ(events[0].echo().data(), "test");
  EXPECT_EQ(events[1].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events[1].has_session());

  // Queuing an event after the session has started should send it right away
  data->set_data("test2");
  manager->SendOrQueueEventsForSession(&daemon, app_name, {event});
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the second echo event before the timeout.
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 3; }));
  }
  EXPECT_TRUE(events.size() == 3);
  EXPECT_EQ(events[2].kind(), proto::Event::ECHO);
  EXPECT_TRUE(events[2].has_echo());
  EXPECT_EQ(events[2].echo().data(), "test2");

  // Kill read thread to cleanly exit test.
  event_buffer.InterruptWriteEvents();
  read_thread.join();
}
}  // namespace profiler
