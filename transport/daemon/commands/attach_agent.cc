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
#include "attach_agent.h"

#include <string>

#include "proto/common.pb.h"
#include "utils/process_manager.h"

using grpc::Status;
using grpc::StatusCode;
using profiler::proto::Event;
using std::string;

namespace profiler {

Status AttachAgent::ExecuteOn(Daemon *daemon) {
  // Make sure the pid is valid.
  int32_t pid = command().pid();
  string app_name = ProcessManager::GetCmdlineForPid(pid);
  if (app_name.empty()) {
    return Status(StatusCode::NOT_FOUND,
                  "Process isn't running. Cannot attach agent.");
  }

  bool attachable = daemon->TryAttachAppAgent(
      pid, app_name, data_.agent_lib_file_name(), data_.agent_config_path());
  if (!attachable) {
    Event event;
    event.set_pid(pid);
    event.set_kind(Event::AGENT);
    auto *status = event.mutable_agent_data();
    status->set_status(proto::AgentData::UNATTACHABLE);
    daemon->buffer()->Add(event);
  }

  return Status::OK;
}

}  // namespace profiler
