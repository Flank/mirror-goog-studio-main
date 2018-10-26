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
#include "perfd/event_buffer.h"
#include "perfd/event_writer.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/count_down_latch.h"
#include "utils/fake_clock.h"

#include <gtest/gtest.h>
#include <condition_variable>
#include <list>
#include <mutex>
#include <thread>
#include <vector>

namespace profiler {
using proto::Event;
using proto::EventGroup;

class TestEventWriter final : public EventWriter {
 public:
  bool Write(const proto::Event& event) override {
    std::unique_lock<std::mutex> lock(mu_);
    events_.push_back(event);
    cv_.notify_all();
    return true;
  }

  std::list<proto::Event>& GetEvents() { return events_; }
  std::condition_variable& GetConditionVariable() { return cv_; }
  std::mutex& GetMutex() { return mu_; }

 private:
  std::condition_variable cv_;
  std::mutex mu_;
  std::list<proto::Event> events_;
};

void ReadEvents(EventBuffer* buffer, TestEventWriter* writer) {
  buffer->WriteEventsTo(writer);
}

void CreateTestData(FakeClock& clock, EventBuffer& buffer) {
  Event event;
  clock.Elapse(1);
  // Add 2 events to the same event group.
  event.set_event_id(1);
  event.set_session_id(1);
  event.set_kind(Event::SESSION);
  event.set_type(Event::SESSION_STARTED);
  buffer.Add(event);
  clock.Elapse(5);
  event.set_kind(Event::SESSION);
  event.set_type(Event::SESSION_ENDED);
  buffer.Add(event);
  clock.Elapse(1);
  // Add 1 event to a new event group.
  event.set_event_id(2);
  event.set_session_id(2);
  event.set_kind(Event::PROCESS);
  event.set_type(Event::PROCESS_STARTED);
  buffer.Add(event);
}

TEST(EventBuffer, GettingEventGroup) {
  FakeClock clock;
  EventBuffer buffer(&clock);
  CreateTestData(clock, buffer);

  // Get 2 element group.
  EventGroup group;
  buffer.GetGroup(1, &group);

  // Validate
  EXPECT_EQ(1, group.event_id());
  EXPECT_EQ(2, group.events_size());
  EXPECT_EQ(Event::SESSION_STARTED, group.events().Get(0).type());
  EXPECT_EQ(Event::SESSION_ENDED, group.events().Get(1).type());
}

TEST(EventBuffer, GettingEventsFiltered) {
  FakeClock clock;
  EventBuffer buffer(&clock);
  CreateTestData(clock, buffer);

  // Validate we get a group back for events that fit in group.
  EXPECT_EQ(1,
            buffer.Get(1, Event::SESSION, Event::SESSION_ENDED, 0, 5).size());

  // Validate we get the group back if the start session is before the requested
  // time, and the end is greater than the event end.
  EXPECT_EQ(1,
            buffer.Get(1, Event::SESSION, Event::SESSION_ENDED, 3, 7).size());
}

TEST(EventBuffer, ReadWriteEvents) {
  FakeClock clock;
  EventBuffer buffer(&clock);
  TestEventWriter writer;
  CreateTestData(clock, buffer);
  std::unique_lock<std::mutex> lock(writer.GetMutex());
  std::list<proto::Event>* events = &writer.GetEvents();
  std::thread readThread = std::thread(ReadEvents, &buffer, &writer);
  // Expect that we reach 3 events before the timeout.
  EXPECT_TRUE(writer.GetConditionVariable().wait_for(
      lock, std::chrono::milliseconds(1000),
      [events] { return events->size() == 3; }));
  EXPECT_EQ(3, events->size());

  // Kill read thread to cleanly exit test.
  buffer.InterruptWriteEvents();
  readThread.join();
}

TEST(EventBuffer, BufferOverflowOfEvents) {
  FakeClock clock;
  EventBuffer buffer(&clock, 4, 4);
  TestEventWriter writer;
  // Create 6 events.
  CreateTestData(clock, buffer);
  CreateTestData(clock, buffer);
  std::unique_lock<std::mutex> lock(writer.GetMutex());
  std::list<proto::Event>* events = &writer.GetEvents();
  std::thread readThread = std::thread(ReadEvents, &buffer, &writer);
  // Expect that we reach 4 events before the timeout.
  EXPECT_TRUE(writer.GetConditionVariable().wait_for(
      lock, std::chrono::milliseconds(1000),
      [events] { return events->size() == 4; }));
  EXPECT_EQ(4, events->size());
  // We write SESSION_STARTED, SESSION_ENDED, PROCESS_STARTED,
  // SESSION_STARTED, SESSION_ENDED, PROCESS_STARTED.
  // Our ring buffer size is limited to 4 elements.
  // As such we expect to get back PROCESS_STARTED, SESSION_STARTED,
  // SESSION_ENDED, PROCESS_STARTED.
  // We expect to get only 4 elements back because we don't start listening to
  // events until after we insert our initial data.
  Event::Type expected[] = {Event::PROCESS_STARTED, Event::SESSION_STARTED,
                            Event::SESSION_ENDED, Event::PROCESS_STARTED};
  int i = 0;
  for (auto it = events->begin(); it != events->end(); ++it, ++i) {
    EXPECT_EQ(expected[i], it->type());
  }

  // Kill read thread to cleanly exit test.
  buffer.InterruptWriteEvents();
  readThread.join();
}

TEST(EventBuffer, ConcurrentWrite) {
  int thread_count = 5;
  FakeClock clock;
  EventBuffer buffer(&clock);
  CountDownLatch latch(thread_count);

  std::vector<std::thread*> data_writers;
  for (int i = 0; i < thread_count; i++) {
    data_writers.push_back(new std::thread([&clock, &buffer, &latch] {
      CreateTestData(clock, buffer);
      latch.CountDown();
    }));
  }

  latch.Await();
  for (auto writer : data_writers) {
    if (writer->joinable()) {
      writer->join();
    }
  }

  // We should expect 10 events in group 1, and 5 events in group 2
  // Get 2 element group.
  EventGroup group1;
  EventGroup group2;
  buffer.GetGroup(1, &group1);
  buffer.GetGroup(2, &group2);
  EXPECT_EQ(1, group1.event_id());
  EXPECT_EQ(10, group1.events_size());
  EXPECT_EQ(2, group2.event_id());
  EXPECT_EQ(5, group2.events_size());
}
}  // namespace profiler
