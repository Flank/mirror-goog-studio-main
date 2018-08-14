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

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>

#include "command_cmd.h"
#include "dump.h"
#include "shell_command.h"

namespace deployer {

namespace {
const std::string kAgent = "agent.so";
const std::string kDex = "support.dex";
const std::string kConfig = "config.pb";
}  // namespace

void SwapCommand::ParseParameters(int argc, char** argv) {
  int size = -1;
  force_update_ = false;
  if (argc < 2) {
    std::cerr << "Not enough arguments for swap command: swap <force> <pb_size>" << std::endl;
    return;
  }

  force_update_ = atoi(argv[0]);
  size = atoi(argv[1]);

  std::string data;
  std::copy_n(std::istreambuf_iterator<char>(std::cin), size, std::back_inserter(data));

  if (!request_.ParseFromString(data)) {
    std::cerr << "Could not parse swap configuration proto." << std::endl;
    return;
  }

  // TODO(noahz): Better way of handling the "update" scenario.
  // If we modify/delete the agent after ART has already loaded it, the app
  // will crash, so we want to be careful here.

  if (argc == 1) {
    std::istringstream(argv[0]) >> force_update_;
    std::cout << force_update_ << std::endl;
  }

  // Set this value here so we can re-use it in other methods.
  target_dir_ = "/data/data/" + request_.package_name() + "/.studio/";
  ready_to_run_ = true;
}

bool SwapCommand::Run(const Workspace& workspace) {
  std::string source_dir = workspace.GetBase() + "/bin/";

  if (!Setup(source_dir)) {
    return false;
  }

  // Create the agent config, passing the agent the swap request and the
  // location of the instrumentation library.
  proto::AgentConfig agent_config;
  agent_config.set_allocated_swap_request(&request_);

  // TODO(noahz): Skip all this and pass to agent via the attach-agent command?

  // Write out the configuration file.
  std::ofstream ofile(source_dir + kConfig, std::ios::binary);
  std::string configurationString;
  if (!agent_config.SerializeToString(&configurationString)) {
    std::cerr << "Could not write agent configuration proto." << std::endl;
    return false;
  }
  ofile << configurationString;
  ofile.close();

  // Copy to agent directory.
  std::string output;
  if (!RunCmd("cp", User::APP_PACKAGE,
              {source_dir + kConfig, target_dir_ + kConfig}, &output)) {
    std::cerr << "Could not copy agent config." << output << std::endl;
    return false;
  }

  // Get the pid(s) of the running application using the package name.
  if (!RunCmd("pidof", User::SHELL_USER, {request_.package_name()}, &output)) {
    std::cerr << "Could not get application pid." << output << std::endl;
    return false;
  }

  int pid;
  std::istringstream pids(output);  // This constructor copies 'output', so we
                                    // can re-use output safely.

  // Attach the agent to the application process(es)
  CmdCommand cmd;
  while (pids >> pid) {
    if (!cmd.AttachAgent(pid, target_dir_ + kAgent, {target_dir_ + kConfig},
                         &output)) {
      std::cerr << "Could not attach agent to process." << output << std::endl;
      return false;
    }
  }

  return true;
}

bool SwapCommand::Setup(const std::string& source_dir) {
  // Note the use of shell commands for what would typically be regular stdlib
  // filesystem io. This is because the installer does not have permissions in
  // the /data/data/<app> directory and needs to utilize run-as.

  // Check if the agent has already been copied to the app directory.
  if (!force_update_ &&
      RunCmd("stat", User::APP_PACKAGE, {target_dir_ + kAgent}, nullptr)) {
    return true;
  }

  std::string output;
  // We have to run the following three commands as the application, because
  // we otherwise do not have access to the application's data directory.
  if (!RunCmd("mkdir", User::APP_PACKAGE, {"-p", target_dir_}, &output)) {
    std::cerr << "Could not create .studio directory." << output << std::endl;
    return false;
  }

  if (!RunCmd("cp", User::APP_PACKAGE,
              {source_dir + kAgent, target_dir_ + kAgent}, &output)) {
    std::cerr << "Could not copy agent binary." << output << std::endl;
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
    return cmd.RunAs(params, request_.package_name(), output);
  } else {
    return cmd.Run(params, output);
  }

  return true;
}

}  // namespace deployer
