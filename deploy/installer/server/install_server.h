/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef INSTALL_SERVER_H
#define INSTALL_SERVER_H

#include <memory>
#include <string>

#include "tools/base/deploy/common/proto_pipe.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// Object that can be used to run an install server in the current process.
class InstallServer {
 public:
  InstallServer(int input_fd, int output_fd)
      : input_(input_fd), output_(output_fd) {}

  // Runs an install server in this process. This blocks until the server
  // finishes running.
  void Run();

 private:
  ProtoPipe input_;
  ProtoPipe output_;

  void Acknowledge();
  void Pump();

  void HandleRequest(const proto::InstallServerRequest& request);

  void HandleCheckSetup(const proto::CheckSetupRequest& request,
                        proto::CheckSetupResponse* response) const;

  void HandleOverlayUpdate(const proto::OverlayUpdateRequest& request,
                           proto::OverlayUpdateResponse* response) const;

  void HandleGetAgentExceptionLog(
      const proto::GetAgentExceptionLogRequest& request,
      proto::GetAgentExceptionLogResponse* response) const;

  bool DoesOverlayIdMatch(const std::string& overlay_folder,
                          const std::string& expected_id) const;
};

// Starts an install server in a new process. Returns nullptr if the install
// server can't be started for any reason.
std::unique_ptr<InstallClient> StartInstallServer(
    Executor& executor, const std::string& server_path,
    const std::string& package_name, const std::string& exec_name);

}  // namespace deploy

#endif