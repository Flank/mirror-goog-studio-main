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

#ifndef INSTALLER_SWAP_COMMAND_H_
#define INSTALLER_SWAP_COMMAND_H_

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class SwapCommand : public AgentInteractionCommand {
 public:
  SwapCommand(Workspace& workspace)
      : AgentInteractionCommand(workspace), response_(nullptr) {}
  ~SwapCommand() {}

  void ParseParameters(const proto::InstallerRequest& request) override;
  void Run(proto::InstallerResponse* response) override;

 private:
  proto::SwapRequest request_;
  proto::SwapResponse* response_;

  // Performs a swap by starting the server and attaching agents. Returns
  // SwapResponse::Status::OK if the swap succeeds; returns an appropriate error
  // code otherwise.
  proto::SwapResponse::Status Swap();
};

}  // namespace deploy

#endif  // INSTALLER_SWAP_COMMAND_H_
