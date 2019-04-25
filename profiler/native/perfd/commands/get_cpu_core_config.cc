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
#include "perfd/commands/get_cpu_core_config.h"
#include "perfd/cpu/cpu_config.h"
#include "proto/common.pb.h"

using grpc::Status;
using profiler::proto::Event;

namespace profiler {

Status GetCpuCoreConfig::ExecuteOn(Daemon* daemon) {
  Event event;
  event.set_pid(command().pid());
  event.set_group_id(command().get_cpu_core_config().device_id());
  event.set_kind(Event::CPU_CORE_CONFIG);
  event.set_command_id(command().command_id());
  Status status = CpuConfig::GetCpuCoreConfig(event.mutable_cpu_core_config());
  if (status.ok()) {
    daemon->buffer()->Add(event);
  }
  return status;
}

}  // namespace profiler
