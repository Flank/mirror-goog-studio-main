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

#ifndef OVERLAY_ID_PUSH_H_
#define OVERLAY_ID_PUSH_H_

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/workspace.h"

namespace deploy {

class OverlayIdPushCommand : public Command {
 public:
  OverlayIdPushCommand(Workspace& workspace) : Command(workspace) {}
  virtual ~OverlayIdPushCommand() {}
  virtual void ParseParameters(const proto::InstallerRequest& request);
  virtual void Run(proto::InstallerResponse* response);

 private:
  proto::OverlayIdPush request_;
};

}  // namespace deploy

#endif  // OVERLAY_ID_PUSH_H_
