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

#ifndef AGENT_INTERACTION_H
#define AGENT_INTERACTION_H

#include <google/protobuf/repeated_field.h>

#include <memory>
#include <string>
#include <vector>

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class AgentInteractionCommand : public Command {
 public:
  AgentInteractionCommand(Workspace& workspace) : Command(workspace) {}
  ~AgentInteractionCommand() = default;

 protected:
  // Make sure the app_server and the agent are in the code_cache/startup_agent
  // folder (in app land).
  // Also create an InstallClient to enable app server comm.
  bool PrepareInteraction(proto::Arch arch);

  // Tries to attach an agent to each process in the request; if any agent fails
  // to attach, returns false.
  bool Attach(const std::vector<int>& pids);
  bool Attach(const google::protobuf::RepeatedField<int>& pids);

  std::unique_ptr<proto::OpenAgentSocketResponse> ListenForAgents();
  std::string GetSocketName();

  InstallClient* client_;

  // Set by ParseParameter
  std::string package_name_;

  static void FilterProcessIds(std::vector<int>* process_ids);

  std::unique_ptr<proto::GetAgentExceptionLogResponse> GetAgentLogs();

 private:
  bool interaction_prepared_ = false;

  // Set by PrepareInteraction
  std::string socket_name_;
  std::string agent_filename_;

  bool CheckExist(const std::vector<std::string>& files,
                  std::unordered_set<std::string>* missing_files);

  bool CopyAgent(const std::string& agent_filename);

  std::string AppAgentAbsPath(const std::string& agent_filename);
  std::string AppAgentAbsDir();
};
}  // namespace deploy

#endif
