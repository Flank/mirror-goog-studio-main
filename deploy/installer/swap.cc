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
#include <unordered_map>
#include <vector>

#include <fcntl.h>

#include "command_cmd.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "shell_command.h"

#include "agent.so.cc"
#include "agent_server.cc"

namespace deployer {

namespace {
const std::string kAgentFilename =
    "agent-" + std::string(agent_so_hash) + ".so";
const std::string kServerFilename =
    "server-" + std::string(agent_server_hash) + ".so";
const int kRwFileMode =
    S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRxFileMode =
    S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH;
}  // namespace

// Note: the use of shell commands for what would typically be regular stdlib
// filesystem io is because the installer does not have permissions in the
// /data/data/<app> directory and needs to utilize run-as.

void SwapCommand::ParseParameters(int argc, char** argv) {
  int size = -1;
  if (argc != 1) {
    std::cerr << "Not enough arguments for swap command: swap <pb_size>"
              << std::endl;
    return;
  }

  size = atoi(argv[0]);
  static std::string data;
  std::copy_n(std::istreambuf_iterator<char>(std::cin), size,
              std::back_inserter(data));

  proto::SwapRequest request;
  if (!request.ParseFromString(data)) {
    std::cerr << "Could not parse swap configuration proto." << std::endl;
    return;
  }

  request_bytes_ = data;
  package_name_ = request.package_name();

  // Set this value here so we can re-use it in other methods.
  target_dir_ = "/data/data/" + package_name_ + "/.studio/";
  ready_to_run_ = true;
}

bool SwapCommand::Run(const Workspace& workspace) {
  if (!Setup(workspace)) {
    return false;
  }

  // Start a swap server with fork/exec.
  int read_fd;
  int write_fd;
  if (!StartServer(&read_fd, &write_fd)) {
    return false;
  }

  size_t agent_count = AttachAgents();
  if (agent_count == 0) {
    return false;
  }

  // Both these wrappers will close the fds when they go out of scope.
  deploy::MessagePipeWrapper server_input(write_fd);
  deploy::MessagePipeWrapper server_output(read_fd);

  if (!server_input.Write(request_bytes_)) {
    std::cerr << "Could not write to agent proxy server." << std::endl;
  }

  std::unordered_map<int, proto::SwapResponse> agent_responses;
  auto overall_status = proto::SwapResponse::UNKNOWN;

  // TODO: This loop is at risk of hanging in a multi-agent scenario where
  // activity restart is required - if one agent dies before sending an
  // activity restart message, the server will never exit, as the still-alive
  // agents are permanently stuck waiting on activity restart and will not close
  // their sockets. Server should detect and send an agent death message to the
  // installer.

  std::string response_bytes;
  while (server_output.Read(&response_bytes)) {
    proto::SwapResponse response;
    if (!response.ParseFromString(response_bytes)) {
      std::cerr << "Received unparseable response from agent." << std::endl;
      return false;
    }

    // Any mismatch in statuses means the overall status is error:
    // - ERROR + <ANY> = ERROR, since all agents need to succeed.
    // - RESTART + OK = ERROR , since agents should be in sync for restarts.
    if (agent_responses.size() == 0) {
      overall_status = response.status();
    } else if (response.status() != overall_status) {
      overall_status = proto::SwapResponse::ERROR;
    }

    agent_responses.emplace(response.pid(), response);

    // Don't take actions until we've heard from every agent.
    if (agent_responses.size() == agent_count) {
      if (overall_status == proto::SwapResponse::NEED_ACTIVITY_RESTART) {
        CmdCommand cmd;
        cmd.UpdateAppInfo("all", package_name_, nullptr);
      } else {
        // TODO: Aggregate and send to deployer.
        std::cout << overall_status << std::endl;
      }

      agent_responses.clear();
    }
  }

  return overall_status == proto::SwapResponse::OK;
}

bool SwapCommand::Setup(const Workspace& workspace) noexcept {
  // Make sure the target dir exists.
  std::string output;
  if (!RunCmd("mkdir", User::APP_PACKAGE, {"-p", target_dir_}, &output)) {
    std::cerr << "Could not create .studio directory." << output << std::endl;
    return false;
  }

  if (!CopyBinaries(workspace.GetTmpFolder(), target_dir_)) {
    return false;
  }

  return true;
}

bool SwapCommand::CopyBinaries(const std::string& src_path,
                               const std::string& dst_path) const noexcept {
  // TODO: Cleanup previous version of the binaries?
  // If we modify/delete the agent after ART has already loaded it, the app
  // will crash, so we want to be careful here.

  std::string agent_src_path = src_path + kAgentFilename;
  std::string agent_dst_path = dst_path + kAgentFilename;

  std::string server_src_path = src_path + kServerFilename;
  std::string server_dst_path = dst_path + kServerFilename;

  // Checks if both binaries are in the data folder of the agent. Since the
  // common case expects that both binaries will be present, we do both checks
  // at once to minimize the expected number of additional run-as invocations.
  if (RunCmd("stat", User::APP_PACKAGE, {agent_dst_path, server_dst_path},
             nullptr)) {
    std::cout << "Binaries already in data folder, skipping copy." << std::endl;
    return true;
  }

  // Since we did both checks at once, we still need to individually check for
  // each missing file. This adds one run-as invocation in the "update
  // required" case, but allows for one fewer run-as in the more common "no
  // update" case.

  std::vector<std::string> copy_args;

  if (!RunCmd("stat", User::APP_PACKAGE, {agent_dst_path}, nullptr)) {
    // If the agent library is not already on disk, write it there now.
    if (access(agent_src_path.c_str(), F_OK) == -1) {
      WriteArrayToDisk(agent_so, agent_so_len, agent_src_path);
    }

    // Done using agent_src_path, so its safe to use emplace().
    copy_args.emplace_back(agent_src_path);
  }

  if (!RunCmd("stat", User::APP_PACKAGE, {server_dst_path}, nullptr)) {
    // If the server binary is not already on disk, write it there now.
    if (access(server_src_path.c_str(), F_OK) == -1) {
      WriteArrayToDisk(agent_server, agent_server_len, server_src_path);
    }

    // Done using server_src_path, so its safe to use emplace().
    copy_args.push_back(server_src_path);
  }

  // Add the destination path to the argument list. We don't use dst_path
  // again, so it's safe to use emplace().
  copy_args.push_back(dst_path);

  // Copy the binaries to the agent directory.
  std::string cp_output;
  if (!RunCmd("cp", User::APP_PACKAGE, copy_args, &cp_output)) {
    std::cerr << "Could not copy agent binary: " << cp_output << std::endl;
    return false;
  }

  return true;
}

bool SwapCommand::WriteArrayToDisk(const unsigned char* array,
                                   uint64_t array_len,
                                   const std::string& dst_path) const noexcept {
  int fd = open(dst_path.c_str(), O_WRONLY | O_CREAT, kRwFileMode);
  if (fd == -1) {
    std::cerr << "WriteArrayToDisk(). Unable to open(): "
              << std::string(strerror(errno)) << std::endl;
    return false;
  }
  int written = write(fd, array, array_len);
  if (written == -1) {
    std::cerr << "WriteArrayToDisk(). Unable to write(): "
              << std::string(strerror(errno)) << std::endl;
    return false;
  }

  int close_result = close(fd);
  if (close_result == -1) {
    std::cerr << "WriteArrayToDisk(). Unable to close(): "
              << std::string(strerror(errno)) << std::endl;
    return false;
  }

  chmod(dst_path.c_str(), kRxFileMode);
  return true;
}

bool SwapCommand::StartServer(int* read_fd, int* write_fd) const {
  int read_pipe[2];
  int write_pipe[2];

  if (pipe(write_pipe) < 0 || pipe(read_pipe) < 0) {
    return false;
  }

  int fork_pid = fork();
  if (fork_pid == 0) {
    close(write_pipe[1]);
    close(read_pipe[0]);

    // Map the output of the parent-write pipe to stdin and the input of the
    // parent-read pipe to stdout. This lets us communicate between the
    // swap_server and the installer.
    dup2(write_pipe[0], STDIN_FILENO);
    dup2(read_pipe[1], STDOUT_FILENO);

    close(write_pipe[0]);
    close(read_pipe[1]);

    std::string command = target_dir_ + kServerFilename;

    execlp("run-as", command.c_str() /* argv[0] for run-as*/,
           package_name_.c_str() /* package to execute as */,
           command.c_str() /* command to start swap-server */,
           "1" /* number of agents (hardcoded for now) */,
           (char*)nullptr /* must end in null-terminator */);

    // If we get here, the execlp failed, so we should return false.
    std::cerr << "Could not exec swap_server: " << strerror(errno) << std::endl;
    return false;
  }

  close(write_pipe[0]);
  close(read_pipe[1]);

  *read_fd = read_pipe[0];
  *write_fd = write_pipe[1];
  return true;
}

size_t SwapCommand::AttachAgents() const {
  size_t agent_count = 0;

  // Get the pid(s) of the running application using the package name.
  std::string pidof_output;
  if (!RunCmd("pidof", User::SHELL_USER, {package_name_}, &pidof_output)) {
    std::cerr << "Could not get application pid for package : " + package_name_
              << std::endl;
    return 0;
  }

  int pid;
  std::istringstream pids(pidof_output);

  // Attach the agent to the application process(es).
  CmdCommand cmd;
  while (pids >> pid) {
    std::string output;
    if (!cmd.AttachAgent(pid, target_dir_ + kAgentFilename, {}, &output)) {
      std::cerr << "Could not attach agent to process: " << output << std::endl;
      return 0;
    }
    agent_count++;
  }

  return agent_count;
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
