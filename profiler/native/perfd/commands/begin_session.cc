/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "perfd/commands/begin_session.h"

#include <string>
#include "perfd/sessions/sessions_manager.h"
#include "proto/common.pb.h"
#include "utils/process_manager.h"

using grpc::Status;
using grpc::StatusCode;
using profiler::proto::Event;
using std::string;

namespace profiler {

Status BeginSession::ExecuteOn(Daemon* daemon) {
  // Make sure the pid is valid.
  string app_name = ProcessManager::GetCmdlineForPid(data_.pid());
  if (app_name.empty()) {
    return Status(StatusCode::NOT_FOUND,
                  "Process isn't running. Cannot create session.");
  }
  SessionsManager::GetInstance()->BeginSession(daemon, command().stream_id(),
                                               data_);

  auto session = SessionsManager::GetInstance()->GetLastSession();
  session->StartSamplers();
  if (data_.jvmti_config().attach_agent()) {
    bool attachable = daemon->TryAttachAppAgent(
        data_.pid(), app_name, data_.jvmti_config().agent_lib_file_name());
    if (!attachable) {
      Event event;
      event.set_pid(data_.pid());
      event.set_kind(Event::AGENT);
      auto* status = event.mutable_agent_data();
      status->set_status(proto::AgentData::UNATTACHABLE);
      daemon->buffer()->Add(event);
    }
  }

  return Status::OK;
}

}  // namespace profiler
