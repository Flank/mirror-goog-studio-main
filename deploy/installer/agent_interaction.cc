/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"

namespace deploy {

namespace {
uint64_t socket_counter = 0;
}

bool AgentInteractionCommand::Attach(const std::vector<int>& pids,
                                     const std::string& agent_path) {
  Phase p("AttachAgents");
  CmdCommand cmd(workspace_);
  for (int pid : pids) {
    std::string output;
    LogEvent("Attaching agent: '"_s + agent_path + "'");
    if (!cmd.AttachAgent(pid, agent_path, {GetSocketName()}, &output)) {
      ErrEvent("Could not attach agent to process: "_s + output);
      return false;
    }
  }
  return true;
}

bool AgentInteractionCommand::Attach(
    const google::protobuf::RepeatedField<int>& ppids,
    const std::string& agent_path) {
  std::vector<int> pids;
  for (int pid : ppids) {
    pids.emplace_back(pid);
  }
  return Attach(pids, agent_path);
}

std::string AgentInteractionCommand::GetSocketName() {
  if (socket_name_.empty()) {
    socket_name_ = Socket::kDefaultAddressPrefix + to_string(socket_counter++);
  }
  return socket_name_;
}

}  // namespace deploy
