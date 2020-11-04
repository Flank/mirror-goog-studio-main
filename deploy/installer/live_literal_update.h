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

#ifndef LIVE_LITERAL_H_
#define LIVE_LITERAL_H_

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/installer/workspace.h"

namespace deploy {

class LiveLiteralUpdateCommand : public AgentInteractionCommand {
 public:
  LiveLiteralUpdateCommand(Workspace& workspace)
      : AgentInteractionCommand(workspace) {}
  virtual ~LiveLiteralUpdateCommand() {}

  // From Command
  virtual void ParseParameters(const proto::InstallerRequest& request) override;
  virtual void Run(proto::InstallerResponse* response) override;

 private:
  proto::LiveLiteralUpdateRequest request_;

  // Copied from BaseSwap
  InstallClient* client_ = nullptr;
  std::string package_name_;
  std::vector<int> process_ids_;
  std::string agent_path_;
  int extra_agents_count_;  // Not sure we need this nor how debugger should
                            // update LL yet.
  void Update(const proto::LiveLiteralUpdateRequest& request,
              proto::LiveLiteralUpdateResponse* responsea);
  void FilterProcessIds(std::vector<int>* process_ids);
  proto::LiveLiteralUpdateResponse::Status ListenForAgents();
  void ProcessResponse(proto::LiveLiteralUpdateResponse* response);
  void PrepareAndBuildRequest(proto::LiveLiteralUpdateResponse* response);
  void GetAgentLogs(proto::LiveLiteralUpdateResponse* response);
  void SetUpdateParameters(const std::string& package_name,
                           const std::vector<int>& process_ids,
                           int extra_agents_count) {
    package_name_ = package_name;
    process_ids_ = process_ids;
    extra_agents_count_ = extra_agents_count;
  }
  bool CheckFilesExist(const std::vector<std::string>& files,
                       std::unordered_set<std::string>* missing_files);
};

}  // namespace deploy

#endif  // LIVE_LITERAL_H_
