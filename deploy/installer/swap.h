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

#include <string>

#include "command.h"
#include "deploy.pb.h"

namespace deployer {

class SwapCommand : public Command {
 public:
  SwapCommand() : force_update_(false){};
  ~SwapCommand() {}

  void ParseParameters(int argc, char** argv) override;
  bool Run(const Workspace& workspace) override;

 private:
  std::string package_name_;
  proto::SwapRequest request_;
  std::string target_dir_;
  bool force_update_;

  enum class User { SHELL_USER, APP_PACKAGE };

  // Makes sure everything is ready for ART to attach the JVMI agent to the app.
  // - Make sure the agent shared lib is in the app data folder.
  // - Make sure the  configuration file to app data folder.
  bool Setup(const Workspace& workspace) noexcept;

  // Runs a command with the provided arguments. If run_as_package is true, the
  // command is invoked with 'run-as'. If the command fails, prints the string
  // specified in 'error' to standard error.
  bool RunCmd(const std::string& shell_cmd, User run_as,
              const std::vector<std::string>& args, std::string* output) const;
  std::string GetAgentFilename() const noexcept;
  bool CopyAgent(const std::string& agent_src_path,
                 const std::string& agent_dst_path) const noexcept;
  bool SetupAgent(const std::string& agent_src_path,
                  const std::string& agent_dst_path) const noexcept;
  bool SetupConfigFile(const Workspace& workspace,
                       const std::string& dst_path) noexcept;
  bool WriteAgentToDisk(const std::string& agent_dst_path) const noexcept;
};

}  // namespace deployer

#endif  // INSTALLER_SWAP_COMMAND_H_
