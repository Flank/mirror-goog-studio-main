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

#ifndef OVERLAY_SWAP_H
#define OVERLAY_SWAP_H

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/proto/deploy.pb.h"

#include <memory>
#include <string>
#include <vector>

namespace deploy {

// This class interacts with the install server to perform a swap with overlay
// update. It performs the following steps:
// - Send a SetupCheckRequest to the install server to see if any files (agent,
// agent_server, etc.) need to be copied
// - Start the agent server and send a SwapRequest
// - Send an OverlayUpdateRequest to the install server to update the overlays
class OverlaySwapCommand : public AgentInteractionCommand {
 public:
  OverlaySwapCommand(Workspace& workspace)
      : AgentInteractionCommand(workspace) {}
  virtual ~OverlaySwapCommand() = default;
  virtual void ParseParameters(const proto::InstallerRequest& request) override;
  virtual void Run(proto::InstallerResponse* response) override;

 private:
  proto::OverlaySwapRequest request_;
  std::vector<int> process_ids_;
  int extra_agents_count_;

  bool Swap(const std::unique_ptr<proto::SwapRequest> request,
            proto::SwapResponse* response);

  std::unique_ptr<proto::SwapRequest> PrepareAndBuildRequest();
  void ProcessResponse(proto::SwapResponse* response);

  void BuildOverlayUpdateRequest(proto::OverlayUpdateRequest* request);
  proto::SwapResponse::Status OverlayStatusToSwapStatus(
      proto::OverlayUpdateResponse::Status status);

  void UpdateOverlay(proto::SwapResponse* response);
};

}  // namespace deploy

#endif  // DEPLOYER_BASE_INSTALL