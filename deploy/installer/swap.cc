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

#include "swap.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include <fcntl.h>

#include "command_cmd.h"
#include "dump.h"
#include "shell_command.h"

#include "agent.so.cc"

namespace deployer {

namespace {
const std::string kAgentPrefix = "agent-";
const std::string kAgentSuffix = ".so";
const std::string kConfig = "config.pb";
const int kRwFileMode =  S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRxFileMode =  S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH;
}  // namespace

// In this class the use of shell commands for what would typically be
// regular stdlib filesystem io. This is because the installer does not have
// permissions in the /data/data/<app> directory and needs to utilize run-as.

void SwapCommand::ParseParameters(int argc, char** argv) {
  int size = -1;
  force_update_ = false;
  if (argc < 2) {
    std::cerr << "Not enough arguments for swap command: swap <force> <pb_size>"
              << std::endl;
    return;
  }

  force_update_ = atoi(argv[0]);
  size = atoi(argv[1]);

  static std::string data;
  std::copy_n(std::istreambuf_iterator<char>(std::cin), size,
              std::back_inserter(data));

  if (!request_.ParseFromString(data)) {
    std::cerr << "Could not parse swap configuration proto." << std::endl;
    return;
  }
  package_name_ = request_.package_name();

  // TODO(noahz): Better way of handling the "update" scenario.
  // If we modify/delete the agent after ART has already loaded it, the app
  // will crash, so we want to be careful here.

  if (argc == 1) {
    std::istringstream(argv[0]) >> force_update_;
    std::cout << force_update_ << std::endl;
  }

  // Set this value here so we can re-use it in other methods.
  target_dir_ = "/data/data/" + package_name_ + "/.studio/";
  ready_to_run_ = true;
}

std::string SwapCommand::GetAgentFilename() const noexcept {
  return kAgentPrefix + agent_so_hash + kAgentSuffix;
}

bool SwapCommand::WriteAgentToDisk(const std::string& agent_dst_path) const noexcept {
  int fd = open(agent_dst_path.c_str(), O_WRONLY | O_CREAT, kRwFileMode);
  if (fd == -1) {
    std::cerr << "CopyAgentIfNecessary(). Unable to open()." << std::endl;
    return false;
  }
  int written = write(fd, agent_so, agent_so_len);
  if (written == -1) {
    std::cerr << "CopyAgentIfNecessary(). Unable to write()." << std::endl;
    return false;
  }

  int close_result = close(fd);
  if (close_result == -1) {
    std::cerr << "CopyAgentIfNecessary(). Unable to close()." << std::endl;
    return false;
  }

  chmod(agent_dst_path.c_str(),kRxFileMode);

  return true;
}

bool SwapCommand::CopyAgent(const std::string& agent_src_path,
                            const std::string& agent_dst_path) const noexcept {
  // TODO: Cleanup previous version of the agent ?

  // Check if the agent is already in the app "data" folder.
  std::string test_output;
  if (RunCmd("test", User::APP_PACKAGE, {"-e", agent_dst_path}, &test_output)) {
    std::cout << "Binary already in data folde, skipping copy." << test_output
              << std::endl;
    return true;
  }

  // Make sure the agent library binary is on the disk.
  if (access(agent_src_path.c_str(), F_OK) == -1) {
    WriteAgentToDisk(agent_src_path);
  }

  // Copy to agent directory.
  std::string cp_output;
  if (!RunCmd("cp", User::APP_PACKAGE, {agent_src_path, agent_dst_path},
              &cp_output)) {
    std::cerr << "Could not copy agent binary." << cp_output << std::endl;
    return false;
  }
  return true;
}

bool SwapCommand::Run(const Workspace& workspace) {
  if (!Setup(workspace)) {
    return false;
  }

  // Get the pid(s) of the running application using the package name.
  std::string pidof_output;
  if (!RunCmd("pidof", User::SHELL_USER, {package_name_}, &pidof_output)) {
    std::cerr << "Could not get application pid for package : '" +
                     package_name_ + "':"
              << pidof_output << std::endl;
    return false;
  }

  int pid;
  std::istringstream pids(pidof_output);  // This constructor copies 'output',
                                          // so we can re-use output safely.

  // Attach the agent to the application process(es)
  CmdCommand cmd;
  while (pids >> pid) {
    std::string output;
    if (!cmd.AttachAgent(pid, target_dir_ + GetAgentFilename(),
                         {target_dir_ + kConfig}, &output)) {
      std::cerr << "Could not attach agent to process." << output << std::endl;
      return false;
    }
  }

  return true;
}

bool SwapCommand::SetupAgent(const std::string& agent_src_path,
                             const std::string& agent_dst_path) const noexcept {
  // Check if the agent is already in the app directory.
  if (!force_update_ &&
      RunCmd("stat", User::APP_PACKAGE, {agent_dst_path}, nullptr)) {
    return true;
  }

  if (!CopyAgent(agent_src_path, agent_dst_path)) {
    std::cerr << "Could not copy agent library to app data folder."
              << std::endl;
    return false;
  }
  return true;
}

bool SwapCommand::SetupConfigFile(const Workspace& workspace,
                                  const std::string& dst_path) noexcept {
  std::string config_local_path = workspace.GetTmpFolder() + kConfig;

  // Create the agent config, passing the agent the swap request.
  proto::AgentConfig agent_config;
  agent_config.set_allocated_swap_request(&request_);

  // Write out the configuration file.
  std::ofstream ofile(config_local_path, std::ios::binary);
  std::string configurationString;
  if (!agent_config.SerializeToString(&configurationString)) {
    std::cerr << "Could not write agent configuration proto." << std::endl;
    return false;
  }
  ofile << configurationString;
  ofile.close();

  // Copy to app data folder.
  std::string output;
  if (!RunCmd("cp", User::APP_PACKAGE, {config_local_path, dst_path},
              &output)) {
    return false;
  }
  return true;
}

bool SwapCommand::Setup(const Workspace& workspace) noexcept {
  // Make sure the target dir exists.
  std::string output;
  if (!RunCmd("mkdir", User::APP_PACKAGE, {"-p", target_dir_}, &output)) {
    std::cerr << "Could not create .studio directory." << output << std::endl;
    return false;
  }

  // Make sure the agent is in the data folder.
  std::string agent_src_path = workspace.GetTmpFolder() + GetAgentFilename();
  std::string agent_dst_path = target_dir_ + GetAgentFilename();
  if (!SetupAgent(agent_src_path, agent_dst_path)) {
    return false;
  }

  // Write the config file in the data folder.
  if (!SetupConfigFile(workspace, target_dir_ + kConfig)) {
    return false;
  }

  return true;
}

bool SwapCommand::RunCmd(const std::string& shell_cmd, User run_as,
                         const std::vector<std::string>& args,
                         std::string* output) const {
  ShellCommandRunner cmd(shell_cmd);

  std::string params;
  for (auto& arg : args) {
    params.append(arg);
    params.append(" ");
  }

  if (run_as == User::APP_PACKAGE) {
    return cmd.RunAs(params, package_name_, output);
  } else {
    return cmd.Run(params, output);
  }

  return true;
}

}  // namespace deployer
