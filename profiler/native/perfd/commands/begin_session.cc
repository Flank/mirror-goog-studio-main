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

#include <unistd.h>
#include <string>

#include "perfd/sessions/sessions_manager.h"
#include "proto/common.pb.h"
#include "utils/log.h"
#include "utils/process_manager.h"

using grpc::Status;
using grpc::StatusCode;
using profiler::proto::AgentData;
using profiler::proto::Event;
using std::string;

namespace profiler {

Status BeginSession::ExecuteOn(Daemon* daemon) {
  // Make sure the pid is valid.
  int32_t pid = command().pid();
  string app_name = ProcessManager::GetCmdlineForPid(pid);
  if (app_name.empty()) {
    return Status(StatusCode::NOT_FOUND,
                  "Process isn't running. Cannot create session.");
  }
  SessionsManager::Instance()->BeginSession(daemon, command().stream_id(), pid,
                                            data_);

  auto session = SessionsManager::Instance()->GetLastSession();
  session->StartSamplers();
  if (data_.jvmti_config().attach_agent()) {
    switch (daemon->GetAgentStatus(pid)) {
      case AgentData::UNSPECIFIED:
        // Agent has not been attached yet.
        if (daemon->TryAttachAppAgent(
                pid, app_name, data_.jvmti_config().agent_lib_file_name(),
                data_.jvmti_config().agent_config_path())) {
          // Wait for agent to be attached so the command can be forwarded to
          // agent.
          int32_t count = 0;
          while (count < kAgentStatusRetries &&
                 daemon->GetAgentStatus(pid) != AgentData::ATTACHED) {
            usleep(kAgentStatusRateUs);
            ++count;
          }
          if (count == kAgentStatusRetries) {
            Log::W("[BeginSession] Agent not yet attached.");
          }
        } else {
          // Agent is unattachable.
          Event event;
          event.set_pid(pid);
          event.set_kind(Event::AGENT);
          auto* status = event.mutable_agent_data();
          status->set_status(proto::AgentData::UNATTACHABLE);
          daemon->buffer()->Add(event);
        }
        break;
      case AgentData::ATTACHED:
        // Agent is already attached. It will handle the command to initialize
        // profilers if not yet.
        break;
      case AgentData::UNATTACHABLE:
        // Agent is unattachable. Abort.
        break;
      default:
        break;
    }
  }

  return Status::OK;
}

}  // namespace profiler
