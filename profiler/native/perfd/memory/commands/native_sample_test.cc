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
#include "start_native_sample.h"
#include "stop_native_sample.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "daemon/event_writer.h"
#include "perfd/common/fake_perfetto.h"
#include "perfd/common/perfetto_manager.h"
#include "perfd/memory/native_heap_manager.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

using std::string;

using profiler::proto::MemoryNativeTrackingData;
using ::testing::_;
using ::testing::Return;

namespace profiler {
class MockNativeHeapManager final : public NativeHeapManager {
 public:
  explicit MockNativeHeapManager(FileCache* file_cache,
                                 PerfettoManager& perfetto_manager)
      : NativeHeapManager(file_cache, perfetto_manager) {}

  MOCK_CONST_METHOD3(StartSample, bool(int64_t ongoing_capture_id,
                                       const proto::StartNativeSample& config,
                                       std::string* error_message));
  MOCK_CONST_METHOD2(StopSample,
                     bool(int64_t capture_id, std::string* error_message));
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
TEST(NativeSampleTest, CommandsGeneratesEvents) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);
  std::mutex mutex;
  proto::Command command;
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);
  std::shared_ptr<FakePerfetto> perfetto(new FakePerfetto());
  PerfettoManager perfetto_manager(perfetto);
  MockNativeHeapManager heap_manager(&file_cache, perfetto_manager);
  EXPECT_CALL(heap_manager, StartSample(_, _, _)).WillRepeatedly(Return(true));
  EXPECT_CALL(heap_manager, StopSample(_, _)).WillRepeatedly(Return(true));

  // Start the event writer to listen for incoming events on a separate
  // thread.
  std::condition_variable cv;
  std::vector<proto::Event> events;
  TestEventWriter writer(&events, &cv);
  std::thread read_thread(
      [&writer, &event_buffer] { event_buffer.WriteEventsTo(&writer); });

  // Execute the start command
  clock.SetCurrentTime(10);
  command.set_type(proto::Command::START_NATIVE_HEAP_SAMPLE);
  StartNativeSample::Create(command, &heap_manager)->ExecuteOn(&daemon);
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a status, start and end event
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 2; }));
  }
  EXPECT_EQ(events[0].kind(), proto::Event::MEMORY_NATIVE_SAMPLE_CAPTURE);
  EXPECT_TRUE(events[0].has_memory_native_sample());
  EXPECT_EQ(events[0].memory_native_sample().start_time(), 10);
  EXPECT_EQ(events[0].memory_native_sample().end_time(), LLONG_MAX);

  EXPECT_EQ(events[1].kind(), proto::Event::MEMORY_NATIVE_SAMPLE_STATUS);
  EXPECT_TRUE(events[1].has_memory_native_tracking_status());
  EXPECT_EQ(events[1].memory_native_tracking_status().status(),
            MemoryNativeTrackingData::SUCCESS);
  EXPECT_EQ(events[1].memory_native_tracking_status().start_time(), 10);
  EXPECT_EQ(events[1].memory_native_tracking_status().failure_message(), "");


  // Execute the stop command
  clock.SetCurrentTime(20);
  command.set_type(proto::Command::STOP_NATIVE_HEAP_SAMPLE);
  command.mutable_stop_native_sample()->set_start_time(10);
  StopNativeSample::Create(command, &heap_manager)->ExecuteOn(&daemon);
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a status, start and end event
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 4; }));
  }

  EXPECT_EQ(events[2].kind(), proto::Event::MEMORY_NATIVE_SAMPLE_STATUS);
  EXPECT_TRUE(events[2].has_memory_native_tracking_status());
  EXPECT_EQ(events[2].memory_native_tracking_status().status(),
            MemoryNativeTrackingData::NOT_RECORDING);
  EXPECT_EQ(events[2].memory_native_tracking_status().start_time(), 10);
  EXPECT_EQ(events[2].memory_native_tracking_status().failure_message(), "");

  EXPECT_EQ(events[3].kind(), proto::Event::MEMORY_NATIVE_SAMPLE_CAPTURE);
  EXPECT_TRUE(events[3].has_memory_native_sample());
  EXPECT_EQ(events[3].memory_native_sample().start_time(), 10);
  EXPECT_EQ(events[3].memory_native_sample().end_time(), 20);

  // Kill read thread to cleanly exit test.
  event_buffer.InterruptWriteEvents();
  read_thread.join();
}
}  // namespace profiler
