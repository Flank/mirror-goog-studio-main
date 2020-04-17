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

using namespace profiler::perfetto;
using profiler::perfetto::proto::QueryParameters;
using profiler::perfetto::proto::TraceEventsResult;

typedef QueryParameters::TraceEventsParameters TraceEventsParameters;

void TraceEventsRequestHandler::PopulateTraceEvents(
    TraceEventsParameters params, TraceEventsResult* result) {
  if (result == nullptr ||
      params.criteria_case() == TraceEventsParameters::CRITERIA_NOT_SET) {
    return;
  }

  std::unordered_map<long, TraceEventsResult::ThreadTraceEvents*> thread_map;

  std::string query_string;
  switch (params.criteria_case()) {
    case TraceEventsParameters::kProcessId:
      query_string =
          "SELECT thread.tid, slice.id, slice.ts, slice.dur, slice.name, "
          "       slice.depth, slice.parent_id "
          "FROM slice "
          "     INNER JOIN thread_track ON thread_track.id = slice.track_id "
          "     INNER JOIN thread using(utid) "
          "     INNER JOIN process using(upid) "
          "WHERE process.pid = " +
          std::to_string(params.process_id()) +
          " "
          "ORDER BY thread.tid asc, ts asc;";
      break;
    case TraceEventsParameters::kThreadId:
      query_string =
          "SELECT thread.tid, slice.id, slice.ts, slice.dur, slice.name, "
          "       slice.depth, slice.parent_id "
          "FROM slice "
          "     INNER JOIN thread_track ON thread_track.id = slice.track_id "
          "     INNER JOIN thread using(utid) "
          "WHERE thread.tid = " +
          std::to_string(params.thread_id()) +
          " "
          "ORDER BY thread.tid asc, ts asc;";
      break;
    default:
      std::cerr << "TraceEventsParameters with no criteria set." << std::endl;
  }

  auto it_events = tp_->ExecuteQuery(query_string);
  while (it_events.Next()) {
    auto thread_id = it_events.Get(0).long_value;

    if (thread_map.find(thread_id) == thread_map.end()) {
      thread_map[thread_id] = result->add_thread();
      thread_map[thread_id]->set_thread_id(thread_id);
    }

    auto event_proto = thread_map[thread_id]->add_trace_event();

    auto event_id = it_events.Get(1).long_value;
    event_proto->set_id(event_id);

    auto ts_nanos = it_events.Get(2).long_value;
    event_proto->set_timestamp_nanoseconds(ts_nanos);

    auto dur_nanos = it_events.Get(3).long_value;
    event_proto->set_duration_nanoseconds(dur_nanos);

    auto event_name_sql_value = it_events.Get(4);
    if (event_name_sql_value.is_null()) {
      event_proto->set_name("Unknown");
    } else {
      event_proto->set_name(event_name_sql_value.string_value);
    }

    auto depth = it_events.Get(5).long_value;
    event_proto->set_depth(depth);

    auto parent_id = it_events.Get(6).long_value;
    event_proto->set_parent_id(parent_id);
  }
}
