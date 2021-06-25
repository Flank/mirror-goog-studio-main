/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef LIVE_EDIT_H_
#define LIVE_EDIT_H_

#include "tools/base/deploy/installer/agent_interaction.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/installer/workspace.h"

namespace deploy {
class LiveEditCommand : public AgentInteractionCommand {
 public:
  LiveEditCommand(Workspace& workspace) : AgentInteractionCommand(workspace) {}
  virtual ~LiveEditCommand() {}

  // From Command
  virtual void ParseParameters(const proto::InstallerRequest& request) override;
  virtual void Run(proto::InstallerResponse* response) override;

 private:
  proto::LiveEditRequest request_;
  std::vector<int> process_ids_;
};
}  // namespace deploy
#endif  // LIVE_EDIT_H_
