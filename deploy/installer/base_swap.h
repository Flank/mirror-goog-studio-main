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

#ifndef BASE_SWAP_H
#define BASE_SWAP_H

#include <string>
#include <unordered_set>
#include <vector>

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class BaseSwapCommand : public AgentInteractionCommand {
 public:
  BaseSwapCommand(Workspace& workspace) : AgentInteractionCommand(workspace) {}
  virtual ~BaseSwapCommand() = default;
  virtual void Run(proto::InstallerResponse* response) final;

 protected:
  InstallClient* client_ = nullptr;

  // Swap parameters
  std::string package_name_;
  std::vector<int> process_ids_;
  int extra_agents_count_;

  // Derived classes should override this to set up for the swap, including
  // copying the agent binary to the appropriate location and building the swap
  // request.
  virtual std::unique_ptr<proto::SwapRequest> PrepareAndBuildRequest() = 0;

  // Derived classes should override this to handle the SwapResponse returned
  // from the Swap() method, which aggregates all the AgentSwapResponses into a
  // single message.
  virtual void ProcessResponse(proto::SwapResponse* response) = 0;

  // This must be called by derived classes in the ParseParameters method to set
  // up for the swap.
  void SetSwapParameters(const std::string& package_name,
                         const std::vector<int>& process_ids,
                         int extra_agents_count) {
    package_name_ = package_name;
    process_ids_ = process_ids;
    extra_agents_count_ = extra_agents_count;
  }

  // This must be called by derived classes in the PrepareAndBuildRequest method
  // with the path to the agent to be used for swapping.
  void SetAgentPath(const std::string& agent_path) { agent_path_ = agent_path; }

  // Sends a request to the server to check for the existence of files
  // accessible to the target package.
  bool CheckFilesExist(const std::vector<std::string>& files,
                       std::unordered_set<std::string>* missing_files);

 private:
  std::string agent_path_;

  bool Swap(const std::unique_ptr<proto::SwapRequest> request,
            proto::SwapResponse* response);

  // Filter non-app process ids by removing all pids with uids outside of the
  // range [FIRST_APPLICATION_UID, LAST_APPLICATION_UID] in android.os.Process.
  void FilterProcessIds(std::vector<int>* process_ids);

  // Instruct the install-server to open a socket that will listen for agent
  // connections. This method does NOT inform the install-server of how many
  // agents will be connecting, or tell the install-server what SwapRequest to
  // forward the agents.
  proto::SwapResponse::Status ListenForAgents();
};

}  // namespace deploy

#endif  // DEPLOYER_BASE_INSTALL