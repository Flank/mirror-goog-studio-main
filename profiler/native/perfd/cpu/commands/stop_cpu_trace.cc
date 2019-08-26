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
using profiler::proto::Command;
using profiler::proto::Event;
using profiler::proto::TraceStopStatus;

namespace profiler {

namespace {
// "cache/complete" is where the generic bytes rpc fetches contents
constexpr char kCacheLocation[] = "cache/complete/";

// Helper function to stop the tracing. This function works in the async
// environment because it doesn't require a |profiler::StopCpuTrace| object.
void Stop(Daemon* daemon, const profiler::proto::Command command_data,
          TraceManager* trace_manager) {
  auto& stop_command = command_data.stop_cpu_trace();

  int64_t stop_timestamp;
  bool stopped_from_api = stop_command.has_api_stop_metadata();
  if (stopped_from_api) {
    stop_timestamp = stop_command.api_stop_metadata().stop_timestamp();
  } else {
    stop_timestamp = daemon->clock()->GetCurrentTime();
  }

  TraceStopStatus status;
  auto* capture = trace_manager->StopProfiling(
      stop_timestamp, stop_command.configuration().app_name(),
      stop_command.need_trace_response(), &status);

  Event status_event;
  status_event.set_pid(command_data.pid());
  status_event.set_kind(Event::CPU_TRACE_STATUS);
  status_event.set_command_id(command_data.command_id());
  auto* stop_status =
      status_event.mutable_cpu_trace_status()->mutable_trace_stop_status();
  stop_status->CopyFrom(status);

  if (capture != nullptr) {
    if (status.status() == TraceStopStatus::SUCCESS) {
      std::string from_file_name;
      if (stopped_from_api) {
        // The trace file has been sent via SendBytes API before the command
        // arrives.
        from_file_name = CurrentProcess::dir();
        from_file_name.append(kCacheLocation)
            .append(stop_command.api_stop_metadata().trace_name());
      } else {
        // TODO b/133321803 save this move by having Daemon generate a path in
        // the byte cache that traces can output contents to directly.
        from_file_name = capture->configuration.temp_path();
      }
      std::ostringstream oss;
      oss << CurrentProcess::dir() << kCacheLocation << capture->trace_id;
      std::string to_file_name = oss.str();
      DiskFileSystem fs;
      bool move_failed = fs.MoveFile(from_file_name, to_file_name);
      if (move_failed) {
        stop_status->set_status(TraceStopStatus::CANNOT_READ_FILE);
        stop_status->set_error_message("Failed to read trace from device");
      }
    }

    Event event;
    event.set_pid(command_data.pid());
    event.set_kind(Event::CPU_TRACE);
    event.set_group_id(capture->trace_id);
    event.set_is_ended(true);
    event.set_command_id(command_data.command_id());
    event.set_timestamp(capture->end_timestamp);

    auto* trace_info =
        event.mutable_cpu_trace()->mutable_trace_ended()->mutable_trace_info();
    trace_info->set_trace_id(capture->trace_id);
    trace_info->set_from_timestamp(capture->start_timestamp);
    trace_info->set_to_timestamp(capture->end_timestamp);
    trace_info->mutable_configuration()->CopyFrom(capture->configuration);
    trace_info->mutable_start_status()->CopyFrom(capture->start_status);
    trace_info->mutable_stop_status()->CopyFrom(capture->stop_status);
    status_event.set_group_id(capture->trace_id);

    daemon->buffer()->Add(status_event);
    daemon->buffer()->Add(event);
  } else {
    daemon->buffer()->Add(status_event);
  }
}

}  // namespace

Status StopCpuTrace::ExecuteOn(Daemon* daemon) {
  // In order to make this command to return immediately, start a new
  // detached thread to stop CPU recording which which may take several seconds.
  // For example, we may need to wait for several seconds before the trace files
  // from ART to be complete.
  //
  // We need to capture the values of the fields of |this| object because when
  // the thread is executing, |this| object may be recycled.
  profiler::proto::Command command_data = command();
  TraceManager* trace_manager = trace_manager_;
  std::thread worker([daemon, command_data, trace_manager]() {
    Stop(daemon, command_data, trace_manager);
  });
  worker.detach();
  return Status::OK;
}

}  // namespace profiler
