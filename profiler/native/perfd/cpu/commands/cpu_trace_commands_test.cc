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
#include "start_cpu_trace.h"
#include "stop_cpu_trace.h"

#include <gtest/gtest.h>
#include "daemon/event_writer.h"
#include "google/protobuf/util/message_differencer.h"
#include "perfd/cpu/fake_atrace.h"
#include "perfd/cpu/fake_perfetto.h"
#include "perfd/cpu/fake_simpleperf.h"
#include "perfd/cpu/trace_manager.h"
#include "perfd/sessions/sessions_manager.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"
#include "utils/termination_service.h"

#include <condition_variable>
#include <mutex>
#include <thread>
#include <vector>

using google::protobuf::util::MessageDifferencer;
using std::string;

using profiler::proto::CpuTraceConfiguration;
using profiler::proto::CpuTraceType;

namespace profiler {

struct CpuTraceCommandsTest : testing::Test {
  std::unique_ptr<TraceManager> ConfigureDefaultTraceManager(
      const profiler::proto::DaemonConfig::CpuConfig& config) {
    return std::unique_ptr<TraceManager>(new TraceManager(
        &clock_, config, TerminationService::Instance(),
        ActivityManager::Instance(),
        std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
            std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
        std::unique_ptr<AtraceManager>(new AtraceManager(
            std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock_, 50,
            std::unique_ptr<Atrace>(new FakeAtrace(&clock_, false)))),
        std::unique_ptr<PerfettoManager>(new PerfettoManager(
            std::unique_ptr<Perfetto>(new FakePerfetto())))));
  }

  FakeClock clock_;
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

TEST_F(CpuTraceCommandsTest, CommandsGeneratesEvents) {
  FakeClock clock;
  DaemonConfig config(proto::DaemonConfig::default_instance());
  FileCache file_cache(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                       "/");
  EventBuffer event_buffer(&clock);
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config.GetConfig().cpu());
  Daemon daemon(&clock, &config, &file_cache, &event_buffer);

  auto* manager = SessionsManager::Instance();
  proto::BeginSession begin_session;
  manager->BeginSession(&daemon, 0, 0, begin_session);

  // Start the event writer to listen for incoming events on a separate thread.
  std::condition_variable cv;
  std::vector<proto::Event> events;
  TestEventWriter writer(&events, &cv);
  std::thread read_thread(
      [&writer, &event_buffer] { event_buffer.WriteEventsTo(&writer); });

  // Execute the start command
  CpuTraceConfiguration trace_config;
  trace_config.set_app_name("fake_app");
  trace_config.set_initiation_type(
      proto::TraceInitiationType::INITIATED_BY_API);
  auto* user_options = trace_config.mutable_user_options();
  user_options->set_trace_type(CpuTraceType::ATRACE);

  proto::Command command;
  command.set_type(proto::Command::START_CPU_TRACE);
  auto* start = command.mutable_start_cpu_trace();
  start->mutable_configuration()->CopyFrom(trace_config);
  StartCpuTrace::Create(command, trace_manager.get(),
                        SessionsManager::Instance())
      ->ExecuteOn(&daemon);

  std::mutex mutex;
  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive events before the timeout.
    // We should expect a begin-session event, followed by the cpu trace
    // status and info events.
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 3; }));
  }

  EXPECT_EQ(3, events.size());
  EXPECT_TRUE(trace_manager->GetOngoingCapture("fake_app") != nullptr);
  EXPECT_EQ(events[0].kind(), proto::Event::SESSION);
  EXPECT_TRUE(events[0].has_session());
  EXPECT_TRUE(events[0].session().has_session_started());
  EXPECT_EQ(events[1].kind(), proto::Event::CPU_TRACE_STATUS);
  EXPECT_TRUE(events[1].has_cpu_trace_status());
  EXPECT_TRUE(events[1].cpu_trace_status().has_trace_start_status());
  EXPECT_EQ(events[2].kind(), proto::Event::CPU_TRACE);
  EXPECT_FALSE(events[2].is_ended());
  EXPECT_TRUE(events[2].has_cpu_trace());
  EXPECT_TRUE(events[2].cpu_trace().has_trace_started());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config,
      events[2].cpu_trace().trace_started().trace_info().configuration()));

  // Execute the end command
  command.set_type(proto::Command::STOP_CPU_TRACE);
  auto* stop = command.mutable_stop_cpu_trace();
  stop->mutable_configuration()->CopyFrom(trace_config);
  StopCpuTrace::Create(command, trace_manager.get())->ExecuteOn(&daemon);

  {
    std::unique_lock<std::mutex> lock(mutex);
    // Expect that we receive the end status and trace events.
    EXPECT_TRUE(cv.wait_for(lock, std::chrono::milliseconds(1000),
                            [&events] { return events.size() == 5; }));
  }
  EXPECT_EQ(5, events.size());
  EXPECT_TRUE(trace_manager->GetOngoingCapture("fake_app") == nullptr);
  EXPECT_EQ(events[3].kind(), proto::Event::CPU_TRACE_STATUS);
  EXPECT_TRUE(events[3].has_cpu_trace_status());
  EXPECT_TRUE(events[3].cpu_trace_status().has_trace_stop_status());
  EXPECT_EQ(events[4].kind(), proto::Event::CPU_TRACE);
  EXPECT_TRUE(events[4].is_ended());
  EXPECT_TRUE(events[4].has_cpu_trace());
  EXPECT_TRUE(events[4].cpu_trace().has_trace_ended());
  EXPECT_TRUE(MessageDifferencer::Equals(
      trace_config,
      events[4].cpu_trace().trace_ended().trace_info().configuration()));

  // Kill read thread to cleanly exit test.
  event_buffer.InterruptWriteEvents();
  read_thread.join();
}
}  // namespace profiler
