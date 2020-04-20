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

#include "proto/memory_data.pb.h"

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::MemoryNativeTrackingData;
using std::string;

namespace profiler {

Status StartNativeSample::ExecuteOn(Daemon* daemon) {
  auto& config = command().start_native_sample();
  // Used as the group id for this heap dump's events.
  // The raw bytes will be available in the file cache via this id.
  int64_t start_timestamp = daemon->clock()->GetCurrentTime();
  string error_message;
  bool sample_started =
      heap_sampler_->StartSample(start_timestamp, config, &error_message);
  std::vector<Event> events_to_send;
  Event status_event;
  status_event.set_pid(command().pid());
  status_event.set_kind(Event::MEMORY_NATIVE_SAMPLE_STATUS);
  status_event.set_command_id(command().command_id());
  status_event.set_is_ended(true);
  status_event.set_group_id(start_timestamp);
  status_event.set_timestamp(start_timestamp);

  auto* status = status_event.mutable_memory_native_tracking_status();
  if (sample_started) {
    status->set_status(MemoryNativeTrackingData::SUCCESS);
    status->set_start_time(start_timestamp);

    Event start_event;
    start_event.set_pid(command().pid());
    start_event.set_kind(Event::MEMORY_NATIVE_SAMPLE_CAPTURE);
    start_event.set_command_id(command().command_id());
    start_event.set_group_id(start_timestamp);
    start_event.set_timestamp(start_timestamp);
    auto* dump_info = start_event.mutable_memory_native_sample();
    dump_info->set_start_time(start_timestamp);
    dump_info->set_end_time(LLONG_MAX);
    events_to_send.push_back(start_event);

  } else {
    status->set_status(MemoryNativeTrackingData::FAILURE);
    status->set_failure_message(error_message);
  }
  events_to_send.push_back(status_event);
  // For the case of startup tracing, the command could be sent before 
  // the session is created. Either send the events if the session
  // is already alive or queue the events to be sent when the session is
  // created.
  sessions_manager_->SendOrQueueEventsForSession(daemon, config.app_name(),
                                                 events_to_send);
  return Status::OK;
}

}  // namespace profiler
