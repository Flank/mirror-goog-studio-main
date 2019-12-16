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

#include "perfd/cpu/commands/trace_command_utils.h"
#include "perfd/sessions/sessions_manager.h"
#include "proto/cpu.pb.h"

#include <vector>

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::TraceStartStatus;
using std::vector;

namespace profiler {

Status StartCpuTrace::ExecuteOn(Daemon* daemon) {
  auto& start_command = command().start_cpu_trace();

  int64_t start_timestamp;
  if (start_command.has_api_start_metadata()) {
    start_timestamp = start_command.api_start_metadata().start_timestamp();
  } else {
    start_timestamp = daemon->clock()->GetCurrentTime();
  }

  TraceStartStatus start_status;
  auto* capture = trace_manager_->StartProfiling(
      start_timestamp, start_command.configuration(), &start_status);

  Event status_event;
  status_event.set_pid(command().pid());
  status_event.set_kind(Event::CPU_TRACE_STATUS);
  status_event.set_command_id(command().command_id());
  status_event.mutable_cpu_trace_status()
      ->mutable_trace_start_status()
      ->CopyFrom(start_status);

  vector<Event> events_to_send;
  if (capture != nullptr) {
    Event event = PopulateCpuTraceEvent(*capture, command(), false);
    status_event.set_group_id(capture->trace_id);

    events_to_send.push_back(status_event);
    events_to_send.push_back(event);
  } else {
    events_to_send.push_back(status_event);
  }
  // For the case of startup or API-initiated tracing, the command could be
  // sent before the session is created. Either send the events if the session
  // is already alive or queue the events to be sent when the session is
  // created.
  sessions_manager_->SendOrQueueEventsForSession(
      daemon, start_command.configuration().app_name(), events_to_send);

  return Status::OK;
}

}  // namespace profiler
