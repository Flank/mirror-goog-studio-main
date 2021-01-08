/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "scheduling_request_handler.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::SchedulingEventsParameters
    SchedulingEventsParameters;
typedef proto::SchedulingEventsResult SchedulingEventsResult;
typedef proto::SchedulingEventsResult::SchedulingEvent SchedulingEvent;

typedef ::perfetto::trace_processor::TraceProcessor TraceProcessor;
typedef ::perfetto::trace_processor::Config Config;

const std::string TESTDATA_PATH(
    "tools/base/profiler/native/trace_processor_daemon/testdata/tank.trace");

const long TANK_PROCESS_PID = 9796;
const long TANK_PROCESS_UNITY_MAIN_THREAD_ID = 9834;

std::unique_ptr<TraceProcessor> LoadTrace(std::string trace_path) {
  Config config;
  config.ingest_ftrace_in_raw_table = false;
  auto tp = TraceProcessor::CreateInstance(config);
  auto read_status = ReadTrace(tp.get(), trace_path.c_str(), {});
  EXPECT_TRUE(read_status.ok());
  return tp;
}

TEST(SchedulingRequestHandlerTest, PopulateEventsByProcessId) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = SchedulingRequestHandler(tp.get());

  SchedulingEventsParameters params_proto;
  params_proto.set_process_id(TANK_PROCESS_PID);

  SchedulingEventsResult result;
  handler.PopulateEvents(params_proto, &result);

  EXPECT_EQ(result.sched_event_size(), 102230);
  EXPECT_EQ(result.num_cores(), 8);

  std::unordered_map<int, long> states_count = {
      {SchedulingEvent::UNKNOWN, 0},
      {SchedulingEvent::RUNNING, 0},
      {SchedulingEvent::RUNNING_FOREGROUND, 0},
      {SchedulingEvent::SLEEPING, 0},
      {SchedulingEvent::SLEEPING_UNINTERRUPTIBLE, 0},
      {SchedulingEvent::WAKE_KILL, 0},
      {SchedulingEvent::WAKING, 0},
      {SchedulingEvent::DEAD, 0},
  };

  for (auto event : result.sched_event()) {
    EXPECT_EQ(event.process_id(), TANK_PROCESS_PID);
    EXPECT_LT(event.cpu(), result.num_cores());

    EXPECT_GE(event.timestamp_nanoseconds(), 0);
    EXPECT_GT(event.duration_nanoseconds(), 0);
    EXPECT_GE(event.priority(), 0);

    states_count[event.state()]++;
  }

  EXPECT_EQ(states_count[SchedulingEvent::UNKNOWN], 0);
  EXPECT_EQ(states_count[SchedulingEvent::RUNNING], 1556);
  EXPECT_EQ(states_count[SchedulingEvent::RUNNING_FOREGROUND], 5020);
  EXPECT_EQ(states_count[SchedulingEvent::SLEEPING], 89828);
  EXPECT_EQ(states_count[SchedulingEvent::SLEEPING_UNINTERRUPTIBLE], 5822);
  EXPECT_EQ(states_count[SchedulingEvent::WAKE_KILL], 0);
  EXPECT_EQ(states_count[SchedulingEvent::WAKING], 0);
  EXPECT_EQ(states_count[SchedulingEvent::DEAD], 4);
}

TEST(SchedulingRequestHandlerTest, PopulateEventsByThreadId) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = SchedulingRequestHandler(tp.get());

  SchedulingEventsParameters params_proto;
  params_proto.set_thread_id(TANK_PROCESS_UNITY_MAIN_THREAD_ID);

  SchedulingEventsResult result;
  handler.PopulateEvents(params_proto, &result);

  EXPECT_EQ(result.sched_event_size(), 11005);
  EXPECT_EQ(result.num_cores(), 8);

  std::unordered_map<int, long> states_count = {
      {SchedulingEvent::UNKNOWN, 0},
      {SchedulingEvent::RUNNING, 0},
      {SchedulingEvent::RUNNING_FOREGROUND, 0},
      {SchedulingEvent::SLEEPING, 0},
      {SchedulingEvent::SLEEPING_UNINTERRUPTIBLE, 0},
      {SchedulingEvent::WAKE_KILL, 0},
      {SchedulingEvent::WAKING, 0},
      {SchedulingEvent::DEAD, 0},
  };

  for (auto event : result.sched_event()) {
    EXPECT_EQ(event.process_id(), TANK_PROCESS_PID);
    EXPECT_EQ(event.thread_id(), TANK_PROCESS_UNITY_MAIN_THREAD_ID);
    EXPECT_LT(event.cpu(), result.num_cores());

    EXPECT_GE(event.timestamp_nanoseconds(), 0);
    EXPECT_GT(event.duration_nanoseconds(), 0);
    EXPECT_GE(event.priority(), 0);

    states_count[event.state()]++;
  }

  EXPECT_EQ(states_count[SchedulingEvent::UNKNOWN], 0);
  EXPECT_EQ(states_count[SchedulingEvent::RUNNING], 599);
  EXPECT_EQ(states_count[SchedulingEvent::RUNNING_FOREGROUND], 3665);
  EXPECT_EQ(states_count[SchedulingEvent::SLEEPING], 3510);
  EXPECT_EQ(states_count[SchedulingEvent::SLEEPING_UNINTERRUPTIBLE], 3231);
  EXPECT_EQ(states_count[SchedulingEvent::WAKE_KILL], 0);
  EXPECT_EQ(states_count[SchedulingEvent::WAKING], 0);
  EXPECT_EQ(states_count[SchedulingEvent::DEAD], 0);
}

TEST(SchedulingRequestHandlerTest, PopulateEventsAllData) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = SchedulingRequestHandler(tp.get());

  SchedulingEventsParameters params_proto;

  SchedulingEventsResult result;
  handler.PopulateEvents(params_proto, &result);

  // Very simple test to make sure we are returning more data than the
  // tests above.
  EXPECT_EQ(result.sched_event_size(), 592967);
  EXPECT_EQ(result.num_cores(), 8);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
