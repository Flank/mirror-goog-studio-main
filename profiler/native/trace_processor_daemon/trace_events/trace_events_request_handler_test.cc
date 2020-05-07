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

#include "trace_events_request_handler.h"

#include <grpc++/grpc++.h>
#include <gtest/gtest.h>
#include <cstdint>
#include <unordered_set>

#include "perfetto/trace_processor/basic_types.h"
#include "perfetto/trace_processor/read_trace.h"
#include "perfetto/trace_processor/trace_processor.h"

namespace profiler {
namespace perfetto {
namespace {

typedef proto::QueryParameters::TraceEventsParameters TraceEventsParameters;
typedef proto::TraceEventsResult TraceEventsResult;

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

TEST(TraceEventsRequestHandlerTest, PopulateEventsByProcessId) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = TraceEventsRequestHandler(tp.get());

  TraceEventsParameters params_proto;
  params_proto.set_process_id(TANK_PROCESS_PID);

  TraceEventsResult result;
  handler.PopulateTraceEvents(params_proto, &result);

  std::unordered_set<int64_t> thread_id_set;
  std::unordered_set<int64_t> event_id_set;
  std::unordered_set<int64_t> parent_id_set;

  // The process has 8 threads with events.
  EXPECT_EQ(result.thread_size(), 8);

  for (auto thread : result.thread()) {
    thread_id_set.insert(thread.thread_id());

    for (auto event : thread.trace_event()) {
      event_id_set.insert(event.id());
      parent_id_set.insert(event.parent_id());

      if (event.depth() > 0) {
        EXPECT_NE(event.parent_id(), 0);
      }
    }
  }

  // Double check that we actually only see data for 8 threads.
  EXPECT_EQ(thread_id_set.size(), 8);

  EXPECT_EQ(event_id_set.size(), 198600);
  EXPECT_EQ(parent_id_set.size(), 38216);

  // Check that all parent_ids references existing events.
  long missing = 0;
  for (auto id : parent_id_set) {
    if (event_id_set.count(id) == 0) {
      missing++;
    }
  }

  // TODO(b/): Investigate this single event that is missing its parent.
  EXPECT_EQ(missing, 1);
}

TEST(TraceEventsRequestHandlerTest, PopulateEventsByThreadId) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = TraceEventsRequestHandler(tp.get());

  TraceEventsParameters params_proto;
  params_proto.set_thread_id(TANK_PROCESS_UNITY_MAIN_THREAD_ID);

  TraceEventsResult result;
  handler.PopulateTraceEvents(params_proto, &result);

  std::unordered_set<int64_t> event_id_set;
  std::unordered_set<int64_t> parent_id_set;

  // Since we queried only one thread, we should expect only one.
  EXPECT_EQ(result.thread_size(), 1);

  auto thread = result.thread(0);
  EXPECT_EQ(thread.thread_id(), TANK_PROCESS_UNITY_MAIN_THREAD_ID);

  for (auto event : thread.trace_event()) {
    event_id_set.insert(event.id());
    parent_id_set.insert(event.parent_id());

    if (event.depth() > 0) {
      EXPECT_NE(event.parent_id(), 0);
    }
  }

  EXPECT_EQ(event_id_set.size(), 119949);
  EXPECT_EQ(parent_id_set.size(), 29906);

  // Check that all parent_ids references existing events.
  long missing = 0;
  for (auto id : parent_id_set) {
    if (event_id_set.count(id) == 0) {
      missing++;
    }
  }

  // TODO(b/): Investigate this single event that is missing its parent.
  EXPECT_EQ(missing, 1);
}

TEST(TraceEventsRequestHandlerTest, PopulateEventsNoIds) {
  auto tp = LoadTrace(TESTDATA_PATH);
  auto handler = TraceEventsRequestHandler(tp.get());

  TraceEventsParameters params_proto;

  TraceEventsResult result;
  handler.PopulateTraceEvents(params_proto, &result);

  EXPECT_EQ(result.thread_size(), 0);
}

}  // namespace
}  // namespace perfetto
}  // namespace profiler
