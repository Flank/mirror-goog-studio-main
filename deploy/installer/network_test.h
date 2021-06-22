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

#ifndef INSTALLER_NETWORK_TEST_H_
#define INSTALLER_NETWORK_TEST_H_

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/workspace.h"
#include "tools/base/deploy/proto/deploy.pb.h"

#include <stdint.h>

namespace deploy {

class NetworkTestCommand : public Command {
 public:
  NetworkTestCommand(Workspace& workspace)
      : Command(workspace),
        request_received_time_ns_(0),
        data_size_bytes_(0) {}
  virtual ~NetworkTestCommand() {}
  virtual void ParseParameters(const proto::InstallerRequest& request);
  virtual void Run(proto::InstallerResponse* response);

 private:
  uint64_t request_received_time_ns_;
  uint32_t data_size_bytes_;
};

}  // namespace deploy

#endif  // INSTALLER_NETWORK_TEST_H_
