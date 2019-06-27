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
#include "heap_dump.h"

#include <climits>

#include "proto/memory.pb.h"

using grpc::Status;
using profiler::proto::Event;
using profiler::proto::HeapDumpStatus;
using profiler::proto::MemoryHeapDumpData;
using std::vector;

namespace profiler {

Status HeapDump::ExecuteOn(Daemon* daemon) {
  // Used as the group id for this heap dump's events.
  // The raw bytes will be available in the file cache via this id.
  int64_t start_timestamp = daemon->clock()->GetCurrentTime();

  Event start_event;
  start_event.set_pid(command().pid());
  start_event.set_kind(Event::MEMORY_HEAP_DUMP);
  start_event.set_command_id(command().command_id());
  start_event.set_group_id(start_timestamp);
  start_event.set_timestamp(start_timestamp);
  auto* dump_info = start_event.mutable_memory_heapdump()->mutable_info();
  dump_info->set_start_time(start_timestamp);
  dump_info->set_end_time(LLONG_MAX);  // LLONG_MAX for ongoing heap dump.

  bool dump_started = heap_dumper_->TriggerHeapDump(
      command().pid(), start_timestamp,
      // Use the start_event to construct the end_event
      [daemon, start_event](bool dump_success) {
        int64_t end_timestamp = daemon->clock()->GetCurrentTime();
        Event end_event;
        end_event.CopyFrom(start_event);
        end_event.set_is_ended(true);
        end_event.set_timestamp(end_timestamp);

        auto* dump_info = end_event.mutable_memory_heapdump()->mutable_info();
        dump_info->set_end_time(end_timestamp);
        dump_info->set_success(dump_success);
        daemon->buffer()->Add(end_event);
      });

  Event status_event;
  status_event.set_pid(command().pid());
  status_event.set_kind(Event::MEMORY_HEAP_DUMP_STATUS);
  status_event.set_command_id(command().command_id());
  status_event.set_is_ended(true);
  status_event.set_group_id(start_timestamp);
  status_event.set_timestamp(start_timestamp);
  auto* status =
      status_event.mutable_memory_heapdump_status()->mutable_status();
  if (dump_started) {
    status->set_status(HeapDumpStatus::SUCCESS);
    status->set_start_time(start_timestamp);
    daemon->buffer()->Add(status_event);
    daemon->buffer()->Add(start_event);
  } else {
    status->set_status(HeapDumpStatus::IN_PROGRESS);
    daemon->buffer()->Add(status_event);
  }

  return Status::OK;
}

}  // namespace profiler
