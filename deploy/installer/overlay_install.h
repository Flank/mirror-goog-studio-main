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

#ifndef OVERLAY_INSTALL_H
#define OVERLAY_INSTALL_H

#include <string>
#include <unordered_set>
#include <vector>

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class OverlayInstallCommand : public Command {
 public:
  OverlayInstallCommand(Workspace& workspace) : Command(workspace) {}
  virtual ~OverlayInstallCommand() = default;
  virtual void ParseParameters(const proto::InstallerRequest& request) override;
  virtual void Run(proto::InstallerResponse* response) override;

 private:
  proto::OverlayInstallRequest request_;
  InstallClient* client_ = nullptr;

  bool SetUpAgent(const std::string& agent,
                  proto::OverlayInstallResponse* overlay_response);
  void UpdateOverlay(proto::OverlayInstallResponse* overlay_response);
  void GetAgentLogs(proto::OverlayInstallResponse* overlay_response);

  // This method was lifted directly from base_swap.cc and should probably be
  // extracted into a shared utility.
  bool CheckFilesExist(const std::vector<std::string>& files,
                       std::unordered_set<std::string>* missing_files);
};

}  // namespace deploy

#endif  // DEPLOYER_BASE_INSTALL