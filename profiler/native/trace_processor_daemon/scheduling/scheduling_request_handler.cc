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

using namespace profiler::perfetto;
using profiler::perfetto::proto::QueryParameters;
using profiler::perfetto::proto::SchedulingEventsResult;

typedef QueryParameters::SchedulingEventsParameters SchedulingEventsParameters;

const std::string STATE_RUNNING = std::string("R");
const std::string STATE_FOREGROUND = std::string("R+");
const std::string STATE_SLEEPING = std::string("S");
const std::string STATE_UNINTERRUPTIBLE = std::string("D");
const std::string STATE_UNINTERRUPTIBLE_WAKEKILL = std::string("DK");
const std::string STATE_WAKEKILL = std::string("K");
const std::string STATE_WAKING = std::string("W");
// Both maps to Task DEAD states, depends on the kernel version.
const std::string STATE_TASK_DEAD_1 = std::string("x");
const std::string STATE_TASK_DEAD_2 = std::string("I");
const std::string STATE_EXIT_DEAD = std::string("X");
const std::string STATE_ZOMBIE = std::string("Z");

// We remove the system swapper scheduling events, because they are a
// placeholder thread to represent when a core is available to run some
// workload.
// We don't filter only by the name because only checking the thread name
// would be error prone since anyone can name a thread "swapper" and we could
// lose data we actually care about.
// Swapper seems to be the only thread that gets assigned tid=0 and utid=0, so
// we use one of it (utid) instead of checking if upid IS NULL. Checking only
// for upid would also drop some other data, like dumpsys and atrace.
const std::string FILTER_SWAPPER = "NOT (thread.name = 'swapper' AND utid = 0) ";

void SchedulingRequestHandler::PopulateEvents(SchedulingEventsParameters params,
                                              SchedulingEventsResult* result) {
  if (result == nullptr) {
    return;
  }

  std::string query_string;
  switch (params.criteria_case()) {
    case SchedulingEventsParameters::kProcessId:
      query_string =
          "SELECT tid, pid, cpu, ts, dur, end_state, priority "
          "FROM sched INNER JOIN thread using(utid) "
          "           INNER JOIN process using(upid) "
          "WHERE pid = " +
          std::to_string(params.process_id()) + " AND " + FILTER_SWAPPER +
          "ORDER BY tid ASC, ts ASC";
      break;
    case SchedulingEventsParameters::kThreadId:
      query_string =
          "SELECT tid, COALESCE(pid, 0) as pid, cpu, ts, dur, end_state, "
          "priority "
          "FROM sched INNER JOIN thread using(utid) "
          "           LEFT JOIN process using(upid) "
          "WHERE tid = " +
          std::to_string(params.thread_id()) + " AND " + FILTER_SWAPPER +
          "ORDER BY tid ASC, ts ASC";
      break;
    case SchedulingEventsParameters::CRITERIA_NOT_SET:
      query_string =
          "SELECT tid, COALESCE(pid, 0) as pid, cpu, ts, dur, end_state, "
          "priority "
          "FROM sched INNER JOIN thread using(utid) "
          "           LEFT JOIN process using(upid) "
          "WHERE " +
          FILTER_SWAPPER + "ORDER BY tid ASC, ts ASC";
      break;
    default:
      std::cerr << "Unknown SchedulingEventsParameters criteria." << std::endl;
      return;
  }

  auto it_sched = tp_->ExecuteQuery(query_string);
  while (it_sched.Next()) {
    auto sched_proto = result->add_sched_event();

    auto thread_id = it_sched.Get(0).long_value;
    sched_proto->set_thread_id(thread_id);

    auto thread_gid = it_sched.Get(1).long_value;
    sched_proto->set_process_id(thread_gid);

    auto cpu_core = it_sched.Get(2).long_value;
    sched_proto->set_cpu(cpu_core);

    auto ts_nanos = it_sched.Get(3).long_value;
    sched_proto->set_timestamp_nanoseconds(ts_nanos);

    auto dur_nanos = it_sched.Get(4).long_value;
    sched_proto->set_duration_nanoseconds(dur_nanos);

    auto priority = it_sched.Get(6).long_value;
    sched_proto->set_priority(priority);

    auto state_sql_value = it_sched.Get(5);
    if (state_sql_value.is_null()) {
      sched_proto->set_state(SchedulingEventsResult::SchedulingEvent::UNKNOWN);
    } else {
      auto state = state_sql_value.string_value;
      if (STATE_RUNNING.compare(state) == 0) {
        sched_proto->set_state(
            SchedulingEventsResult::SchedulingEvent::RUNNING);
      } else if (STATE_FOREGROUND.compare(state) == 0) {
        sched_proto->set_state(
            SchedulingEventsResult::SchedulingEvent::RUNNING_FOREGROUND);
      } else if (STATE_SLEEPING.compare(state) == 0) {
        sched_proto->set_state(
            SchedulingEventsResult::SchedulingEvent::SLEEPING);
      } else if (STATE_UNINTERRUPTIBLE.compare(state) == 0 ||
                 STATE_UNINTERRUPTIBLE_WAKEKILL.compare(state) == 0) {
        sched_proto->set_state(
            SchedulingEventsResult::SchedulingEvent::SLEEPING_UNINTERRUPTIBLE);
      } else if (STATE_WAKEKILL.compare(state) == 0) {
        sched_proto->set_state(
            SchedulingEventsResult::SchedulingEvent::WAKE_KILL);
      } else if (STATE_WAKING.compare(state) == 0) {
        sched_proto->set_state(SchedulingEventsResult::SchedulingEvent::WAKING);
      } else if (STATE_TASK_DEAD_1.compare(state) == 0 ||
                 STATE_TASK_DEAD_2.compare(state) == 0 ||
                 STATE_EXIT_DEAD.compare(state) == 0 ||
                 STATE_ZOMBIE.compare(state) == 0) {
        sched_proto->set_state(SchedulingEventsResult::SchedulingEvent::DEAD);
      } else {
        std::cerr << "Unknown scheduling state encountered: " << state
                  << std::endl;
        sched_proto->set_state(
            SchedulingEventsResult::SchedulingEvent::UNKNOWN);
      }
    }
  }

  // Here we query in the sched table which was the highest cpu core id
  // identified and then we add 1 to have the core count.
  auto it_cpu_count = tp_->ExecuteQuery("SELECT MAX(cpu) FROM sched;");
  if (it_cpu_count.Next()) {
    auto max_core = it_cpu_count.Get(0).long_value;
    result->set_num_cores(max_core + 1);
  }

  if (result->num_cores() == 0) {
    std::cerr << "SchedulingEventsResult with 0 cpu cores." << std::endl;
  }
}
