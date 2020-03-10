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
#include "stop_native_sample.h"

#include "proto/memory_data.pb.h"

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::MemoryHeapDumpData;
using profiler::proto::MemoryNativeTrackingData;
using std::string;

namespace profiler {

Status StopNativeSample::ExecuteOn(Daemon* daemon) {
  auto& config = command().stop_native_sample();
  // Used as the group id for this recording's events.
  // The raw bytes will be available in the file cache via this id.
  int64_t end_timestamp = daemon->clock()->GetCurrentTime();
  string error_message;
  bool sampling_stopped =
      heap_sampler_->StopSample(config.start_time(), &error_message);

  Event status_event;
  status_event.set_pid(command().pid());
  status_event.set_kind(Event::MEMORY_NATIVE_SAMPLE_STATUS);
  status_event.set_command_id(command().command_id());
  status_event.set_is_ended(true);
  status_event.set_group_id(config.start_time());
  status_event.set_timestamp(end_timestamp);
  auto* status = status_event.mutable_memory_native_tracking_status();
  if (sampling_stopped) {
    status->set_status(MemoryNativeTrackingData::NOT_RECORDING);
    status->set_start_time(config.start_time());
  } else {
    status->set_status(MemoryNativeTrackingData::FAILURE);
    status->set_failure_message(error_message);
  }
  daemon->buffer()->Add(status_event);

  if (sampling_stopped) {
    // Send trace file info.
    Event end_event;
    end_event.set_pid(command().pid());
    end_event.set_kind(Event::MEMORY_NATIVE_SAMPLE_CAPTURE);
    end_event.set_command_id(command().command_id());
    end_event.set_group_id(config.start_time());
    end_event.set_timestamp(end_timestamp);
    end_event.set_is_ended(true);
    auto* dump_info = end_event.mutable_memory_native_sample();
    dump_info->set_start_time(config.start_time());
    dump_info->set_end_time(end_timestamp);
    daemon->buffer()->Add(end_event);
  }
  return Status::OK;
}

}  // namespace profiler
