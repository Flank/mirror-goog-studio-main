/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <memory.h>
#include <chrono>
#include <vector>

#include "daemon/event_buffer.h"
#include "foreground_process_tracker.h"
#include "utils/bash_command.h"
#include "utils/fake_clock.h"

namespace {
class SynchronizedEvents {
 public:
  SynchronizedEvents() {}

  void add(profiler::proto::Event event) {
    const std::lock_guard<std::mutex> lock(mutex);
    data_.push_back(event);
  }

  int size() {
    const std::lock_guard<std::mutex> lock(mutex);
    int size = data_.size();
    return size;
  }

  profiler::proto::Event get(int index) {
    const std::lock_guard<std::mutex> lock(mutex);
    profiler::proto::Event event = data_[index];
    return event;
  }

  void clear() {
    const std::lock_guard<std::mutex> lock(mutex);
    data_.clear();
  }

  std::mutex mutex;

 private:
  std::vector<profiler::proto::Event> data_;
};

// Helper class to handle event streaming from the EventBuffer.
// Events added to the event buffer will end up in this EventWriter. Which will
// add them to the |events| vector passed to the constructor.
class TestEventWriter final : public profiler::EventWriter {
 public:
  TestEventWriter(SynchronizedEvents* events) : events_(events) {}

  bool Write(const profiler::proto::Event& event) override {
    events_->add(event);
    return true;
  }

 private:
  SynchronizedEvents* events_;
};

int mock_bash_command_runner_invocation_counter = 0;

// TODO refactor. This synchronization method is fragile.
// The command runner is called on another thread from ForegroundProcessTracker.
// These variables allow the tests to synchronize the two threads.
std::atomic_bool waiting = false;
std::atomic_bool released = false;

class TopActivityMockBashCommandRunner final
    : public profiler::BashCommandRunner {
 public:
  explicit TopActivityMockBashCommandRunner()
      : profiler::BashCommandRunner("") {}

  bool Run(const std::string& parameters, std::string* output) const override {
    bool withinBounds =
        mock_bash_command_runner_invocation_counter < processes.size();
    EXPECT_THAT(withinBounds, testing::IsTrue());

    output->append(processes[mock_bash_command_runner_invocation_counter]);

    mock_bash_command_runner_invocation_counter += 1;

    // Start waiting for the event to be picked up by |WaitForEvents|.
    // Wait until either we get released or the event is consumed.
    waiting.store(true);
    while (!released.load() && waiting.load()) {
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    return true;
  }

 private:
  std::vector<std::string> processes{
      "1:fake.process1/u0a152 (top-activity)",
      "2:fake.process2/u0a152 (top-activity)",
      "3:fake.process3/u0a152 (top-activity)",
      "4:malformed.process4/u0a152",
      "5:malformed.process5 (top-activity)",
      "6:dup.process6/u0a152 (top-activity)",
      "6:dup.process6/u0a152 (top-activity)",
  };
};

class EmptyMockBashCommandRunner final : public profiler::BashCommandRunner {
 public:
  explicit EmptyMockBashCommandRunner() : profiler::BashCommandRunner("") {}

  bool Run(const std::string& parameters, std::string* output) const override {
    output->append("");
    return true;
  }
};

class NotEmptyMockBashCommandRunner final : public profiler::BashCommandRunner {
 public:
  explicit NotEmptyMockBashCommandRunner() : profiler::BashCommandRunner("") {}

  bool Run(const std::string& parameters, std::string* output) const override {
    output->append("foo");
    return true;
  }
};

}  // anonymous namespace

namespace layout_inspector {

class ForegroundProcessTrackerTest : public ::testing::Test {
 public:
  ForegroundProcessTrackerTest() : event_buffer_(new profiler::FakeClock()) {}

  void SetUp() override {
    // Reset counter inbetween tests.
    mock_bash_command_runner_invocation_counter = 0;
    waiting.store(false);
    released.store(false);

    events_.clear();

    writer_ = std::unique_ptr<TestEventWriter>(new TestEventWriter(&events_));
    read_thread_ = std::unique_ptr<std::thread>(new std::thread(
        [this] { event_buffer_.WriteEventsTo(writer_.get()); }));
  }

  void TearDown() override {
    released.store(true);

    // Kill read thread to cleanly exit test.
    event_buffer_.InterruptWriteEvents();
    if (read_thread_->joinable()) {
      read_thread_->join();
    }
    read_thread_ = nullptr;
  }

  // Waits until the specified number of events is received from
  // |process_tracker|.
  void WaitForEvents(ForegroundProcessTracker* process_tracker,
                     int expected_event_count) {
    // Not released, we want to block on events
    released.store(false);
    process_tracker->StartTracking();

    std::chrono::steady_clock::time_point begin =
        std::chrono::steady_clock::now();
    int event_count = 0;

    while (event_count < expected_event_count && !released.load()) {
      std::chrono::steady_clock::time_point end =
          std::chrono::steady_clock::now();
      int ellapsedTime =
          std::chrono::duration_cast<std::chrono::milliseconds>(end - begin)
              .count();

      if (ellapsedTime >= 2000) {
        // timeout
        break;
      }

      if (waiting.load()) {
        // an event is available
        event_count += 1;
        if (event_count < expected_event_count) {
          // keep blocking, otherwise one extra event will be published
          // the last event is effectively available when |released| is set to
          // true.
          waiting.store(false);
        }
      }
      std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    // The call to |process_tracker->StopTracking| will try to join the thread.
    // So we need to release it first. This call to release also makes the last
    // wanted event available.
    released.store(true);
    process_tracker->StopTracking();
  }

  profiler::EventBuffer event_buffer_;

  // TODO storing state in a global variable prevents tests from running in
  // parallel.
  SynchronizedEvents events_;
  std::unique_ptr<TestEventWriter> writer_;
  std::unique_ptr<std::thread> read_thread_;
};

std::unique_ptr<ForegroundProcessTracker> createDefaultForegroundProcessTracker(
    profiler::EventBuffer* event_buffer) {
  return std::unique_ptr<ForegroundProcessTracker>(new ForegroundProcessTracker(
      event_buffer, new TopActivityMockBashCommandRunner(),
      new EmptyMockBashCommandRunner(), new NotEmptyMockBashCommandRunner()));
}

TEST_F(ForegroundProcessTrackerTest, BaseTest) {
  auto process_tracker = createDefaultForegroundProcessTracker(&event_buffer_);

  WaitForEvents(process_tracker.get(), 2);

  EXPECT_THAT(events_.size(), 2);

  EXPECT_THAT(events_.get(0).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(0).layout_inspector_foreground_process().pid(), "1");
  EXPECT_THAT(
      events_.get(0).layout_inspector_foreground_process().process_name(),
      "fake.process1");

  EXPECT_THAT(events_.get(1).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(1).layout_inspector_foreground_process().pid(), "2");
  EXPECT_THAT(
      events_.get(1).layout_inspector_foreground_process().process_name(),
      "fake.process2");
}

TEST_F(ForegroundProcessTrackerTest, StartAndStop) {
  auto process_tracker = createDefaultForegroundProcessTracker(&event_buffer_);

  WaitForEvents(process_tracker.get(), 1);

  EXPECT_THAT(events_.size(), 1);
  EXPECT_THAT(events_.get(0).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(0).layout_inspector_foreground_process().pid(), "1");
  EXPECT_THAT(
      events_.get(0).layout_inspector_foreground_process().process_name(),
      "fake.process1");
  events_.clear();

  WaitForEvents(process_tracker.get(), 1);

  EXPECT_THAT(events_.size(), 1);
  EXPECT_THAT(events_.get(0).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(0).layout_inspector_foreground_process().pid(), "2");
  EXPECT_THAT(
      events_.get(0).layout_inspector_foreground_process().process_name(),
      "fake.process2");
  events_.clear();

  WaitForEvents(process_tracker.get(), 1);

  EXPECT_THAT(events_.size(), 1);
  EXPECT_THAT(events_.get(0).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(0).layout_inspector_foreground_process().pid(), "3");
  EXPECT_THAT(
      events_.get(0).layout_inspector_foreground_process().process_name(),
      "fake.process3");
  events_.clear();
}

TEST_F(ForegroundProcessTrackerTest, ProcessesWithWrongFormatAreNotSent) {
  auto process_tracker = createDefaultForegroundProcessTracker(&event_buffer_);

  // get first 3 well formed processes and try to get last 2 not well formed
  // processes
  WaitForEvents(process_tracker.get(), 5);

  EXPECT_THAT(events_.size(), 3);

  EXPECT_THAT(events_.get(0).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(0).layout_inspector_foreground_process().pid(), "1");
  EXPECT_THAT(
      events_.get(0).layout_inspector_foreground_process().process_name(),
      "fake.process1");

  EXPECT_THAT(events_.get(1).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(1).layout_inspector_foreground_process().pid(), "2");
  EXPECT_THAT(
      events_.get(1).layout_inspector_foreground_process().process_name(),
      "fake.process2");

  EXPECT_THAT(events_.get(2).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(2).layout_inspector_foreground_process().pid(), "3");
  EXPECT_THAT(
      events_.get(2).layout_inspector_foreground_process().process_name(),
      "fake.process3");
}

TEST_F(ForegroundProcessTrackerTest, EventIsSentOnlyOnProcessChange) {
  auto process_tracker = createDefaultForegroundProcessTracker(&event_buffer_);

  // get first 3 well formed processes, try to get next 2 not well formed
  // processes and try to get last two processes. There should be only one
  // because they are the same
  WaitForEvents(process_tracker.get(), 7);

  EXPECT_THAT(events_.size(), 4);

  EXPECT_THAT(events_.get(0).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(0).layout_inspector_foreground_process().pid(), "1");
  EXPECT_THAT(
      events_.get(0).layout_inspector_foreground_process().process_name(),
      "fake.process1");

  EXPECT_THAT(events_.get(1).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(1).layout_inspector_foreground_process().pid(), "2");
  EXPECT_THAT(
      events_.get(1).layout_inspector_foreground_process().process_name(),
      "fake.process2");

  EXPECT_THAT(events_.get(2).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(2).layout_inspector_foreground_process().pid(), "3");
  EXPECT_THAT(
      events_.get(2).layout_inspector_foreground_process().process_name(),
      "fake.process3");

  EXPECT_THAT(events_.get(3).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(3).layout_inspector_foreground_process().pid(), "6");
  EXPECT_THAT(
      events_.get(3).layout_inspector_foreground_process().process_name(),
      "dup.process6");
}

TEST_F(ForegroundProcessTrackerTest, StartStopForegroundProcessNotChanged) {
  auto process_tracker = createDefaultForegroundProcessTracker(&event_buffer_);

  WaitForEvents(process_tracker.get(), 6);

  EXPECT_THAT(events_.size(), 4);

  EXPECT_THAT(events_.get(3).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(3).layout_inspector_foreground_process().pid(), "6");
  EXPECT_THAT(
      events_.get(3).layout_inspector_foreground_process().process_name(),
      "dup.process6");

  WaitForEvents(process_tracker.get(), 1);

  EXPECT_THAT(events_.size(), 5);

  EXPECT_THAT(events_.get(4).kind(),
              profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  EXPECT_THAT(events_.get(4).layout_inspector_foreground_process().pid(), "6");
  EXPECT_THAT(
      events_.get(4).layout_inspector_foreground_process().process_name(),
      "dup.process6");
}

TEST_F(ForegroundProcessTrackerTest, Handshake) {
  auto process_tracker = createDefaultForegroundProcessTracker(&event_buffer_);

  released.store(true);
  TrackingForegroundProcessSupported::SupportType support_type1 =
      process_tracker.get()->IsTrackingForegroundProcessSupported();
  TrackingForegroundProcessSupported::SupportType support_type2 =
      process_tracker.get()->IsTrackingForegroundProcessSupported();
  TrackingForegroundProcessSupported::SupportType support_type3 =
      process_tracker.get()->IsTrackingForegroundProcessSupported();
  TrackingForegroundProcessSupported::SupportType support_type4 =
      process_tracker.get()->IsTrackingForegroundProcessSupported();

  EXPECT_THAT(support_type1, TrackingForegroundProcessSupported::SUPPORTED);
  EXPECT_THAT(support_type2, TrackingForegroundProcessSupported::SUPPORTED);
  EXPECT_THAT(support_type3, TrackingForegroundProcessSupported::SUPPORTED);
  EXPECT_THAT(support_type4, TrackingForegroundProcessSupported::NOT_SUPPORTED);
}

TEST_F(ForegroundProcessTrackerTest,
       Handshake_no_top_activity_no_awake_activities) {
  ForegroundProcessTracker* process_tracker = new ForegroundProcessTracker(
      &event_buffer_, new EmptyMockBashCommandRunner(),
      new NotEmptyMockBashCommandRunner(), new EmptyMockBashCommandRunner());

  TrackingForegroundProcessSupported::SupportType support_type =
      process_tracker->IsTrackingForegroundProcessSupported();

  EXPECT_THAT(support_type, TrackingForegroundProcessSupported::UNKNOWN);
}

TEST_F(ForegroundProcessTrackerTest,
       Handshake_no_top_activity_no_sleeping_activities) {
  ForegroundProcessTracker* process_tracker = new ForegroundProcessTracker(
      &event_buffer_, new EmptyMockBashCommandRunner(),
      new EmptyMockBashCommandRunner(), new NotEmptyMockBashCommandRunner());

  TrackingForegroundProcessSupported::SupportType support_type =
      process_tracker->IsTrackingForegroundProcessSupported();

  EXPECT_THAT(support_type, TrackingForegroundProcessSupported::NOT_SUPPORTED);
}

}  // namespace layout_inspector