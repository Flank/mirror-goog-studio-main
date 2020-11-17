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

#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/server/install_server.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class SwapCommand : public Command {
 public:
  SwapCommand(Workspace& workspace) : Command(workspace), response_(nullptr) {}
  ~SwapCommand() {}

  void ParseParameters(const proto::InstallerRequest& request) override;
  void Run(proto::InstallerResponse* response) override;

 private:
  proto::SwapRequest request_;
  std::string target_dir_;
  proto::SwapResponse* response_;

  std::unique_ptr<InstallClient> client_;

  enum class User { SHELL_USER, APP_PACKAGE };

  // Makes sure everything is ready for ART to attach the JVMI agent to the app.
  // - Make sure the agent shared lib is in the app data folder.
  // - Make sure the  configuration file to app data folder.
  // - Start the install-server.
  bool Setup() noexcept;

  // Performs a swap by starting the server and attaching agents. Returns
  // SwapResponse::Status::OK if the swap succeeds; returns an appropriate error
  // code otherwise.
  proto::SwapResponse::Status Swap() const;

  // Request for the install-server to open a socket and begin listening for
  // agents to connect. Agents connect shortly after they are attached.
  bool WaitForServer() const;

  // Tries to attach an agent to each process in the request; if any agent fails
  // to attach, returns false.
  bool AttachAgents() const;

  // Runs a command with the provided arguments. If run_as_package is true,
  // the command is invoked with 'run-as'. If the command fails, prints the
  // string specified in 'error' to standard error.
  bool RunCmd(const std::string& shell_cmd, User run_as,
              const std::vector<std::string>& args, std::string* output) const;

  // Copies the agent and server binaries to the app /data/data/PKG.
  bool CopyBinaries() const noexcept;
};

}  // namespace deploy

#endif  // INSTALLER_SWAP_COMMAND_H_
