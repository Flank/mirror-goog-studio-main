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
#include "perfd/cpu/commands/trace_command_utils.h"

using profiler::proto::Event;

namespace profiler {

Event PopulateCpuTraceEvent(const ProfilingApp& capture,
                            const profiler::proto::Command& command_data,
                            bool is_end) {
  Event event;
  event.set_pid(command_data.pid());
  event.set_kind(Event::CPU_TRACE);
  event.set_group_id(capture.trace_id);
  event.set_is_ended(is_end);
  event.set_command_id(command_data.command_id());
  if (is_end) event.set_timestamp(capture.end_timestamp);

  auto* trace_info = is_end ? event.mutable_cpu_trace()
                                  ->mutable_trace_ended()
                                  ->mutable_trace_info()
                            : event.mutable_cpu_trace()
                                  ->mutable_trace_started()
                                  ->mutable_trace_info();
  trace_info->set_trace_id(capture.trace_id);
  trace_info->set_from_timestamp(capture.start_timestamp);
  trace_info->set_to_timestamp(capture.end_timestamp);
  trace_info->mutable_configuration()->CopyFrom(capture.configuration);
  trace_info->mutable_start_status()->CopyFrom(capture.start_status);
  if (is_end) {
    trace_info->mutable_stop_status()->CopyFrom(capture.stop_status);
  }
  return event;
}

}  // namespace profiler
