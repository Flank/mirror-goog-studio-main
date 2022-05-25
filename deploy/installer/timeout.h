/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef INSTALLER_TIMEOUT_H_
#define INSTALLER_TIMEOUT_H_

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/workspace.h"
#include "tools/base/deploy/proto/deploy.pb.h"

#include <stdint.h>

namespace deploy {

// A command used to test our desync detection system. It wait for the requested
// amount of time (within max_timeout_ms limit) before returning. It effectively
// simulate a timeout.
class TimeoutCommand : public Command {
 public:
  TimeoutCommand(Workspace& workspace) : Command(workspace), timeout_ms_(0) {}
  virtual ~TimeoutCommand() {}
  virtual void ParseParameters(const proto::InstallerRequest& request);
  virtual void Run(proto::InstallerResponse* response);

 private:
  uint64_t timeout_ms_;
  static constexpr uint64_t max_timeout_ms_ = 10000;
};

}  // namespace deploy

#endif  // INSTALLER_TIMEOUT_H_