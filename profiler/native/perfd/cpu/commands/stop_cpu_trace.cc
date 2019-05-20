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
#include "stop_cpu_trace.h"

#include <sstream>
#include "proto/cpu.pb.h"
#include "utils/fs/disk_file_system.h"
#include "utils/process_manager.h"

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::TraceStopStatus;

namespace profiler {

// "cache/complete" is where the generic bytes rpc fetches contents
constexpr char kCacheLocation[] = "cache/complete/";

Status StopCpuTrace::ExecuteOn(Daemon* daemon) {
  auto& stop_command = command().stop_cpu_trace();

  int64_t stop_timestamp;
  bool stopped_from_api = stop_command.has_api_stop_metadata();
  if (stopped_from_api) {
    stop_timestamp = stop_command.api_stop_metadata().stop_timestamp();
  } else {
    stop_timestamp = daemon->clock()->GetCurrentTime();
  }

  proto::TraceStopStatus::Status status;
  std::string error;
  auto* capture = trace_manager_->StopProfiling(
      stop_timestamp, stop_command.configuration().app_name(), true, &status,
      &error);

  Event status_event;
  status_event.set_pid(command().pid());
  status_event.set_kind(Event::CPU_TRACE_STATUS);
  status_event.set_command_id(command().command_id());
  auto* stop_status =
      status_event.mutable_cpu_trace_status()->mutable_trace_stop_status();

  if (capture != nullptr) {
    if (status == TraceStopStatus::SUCCESS) {
      if (stopped_from_api) {
        std::ostringstream oss;
        oss << capture->trace_id;
        std::string file_name = oss.str();
        daemon->file_cache()->AddChunk(
            file_name, stop_command.api_stop_metadata().trace_content());
        daemon->file_cache()->Complete(file_name);
      } else {
        std::ostringstream oss;

        oss << CurrentProcess::dir() << kCacheLocation << capture->trace_id;
        DiskFileSystem fs;
        // TODO b/133321803 save this move by having Daemon generate a path in
        // the byte cache that traces can output contents to directly.
        bool move_failed =
            fs.MoveFile(capture->configuration.temp_path(), oss.str());
        if (move_failed) {
          status = TraceStopStatus::CANNOT_READ_FILE;
          error = "Failed to read trace from device";
        }
      }
    }

    Event event;
    event.set_pid(command().pid());
    event.set_kind(Event::CPU_TRACE);
    event.set_group_id(capture->trace_id);
    event.set_is_ended(true);
    event.set_command_id(command().command_id());
    event.set_timestamp(capture->end_timestamp);

    auto* trace_info =
        event.mutable_cpu_trace()->mutable_trace_ended()->mutable_trace_info();
    trace_info->set_trace_id(capture->trace_id);
    trace_info->set_from_timestamp(capture->start_timestamp);
    trace_info->set_to_timestamp(capture->end_timestamp);
    auto* config = trace_info->mutable_configuration();
    config->CopyFrom(capture->configuration);

    stop_status->set_status(status);
    stop_status->set_error_message(error);
    status_event.set_group_id(capture->trace_id);
    daemon->buffer()->Add(status_event);

    daemon->buffer()->Add(event);
  } else {
    stop_status->set_status(status);
    stop_status->set_error_message(error);
    daemon->buffer()->Add(status_event);
  }

  return Status::OK;
}

}  // namespace profiler
