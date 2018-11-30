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

#include "tools/base/deploy/installer/swap.h"

#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#include <fcntl.h>
#include <sys/types.h>
#include <sys/wait.h>

#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor.h"

#include "tools/base/deploy/installer/agent.so.h"
#include "tools/base/deploy/installer/agent_server.h"

namespace deploy {

namespace {
const std::string kAgentFilename = "agent-"_s + agent_so_hash + ".so";
const std::string kServerFilename = "server-"_s + agent_server_hash + ".so";
const int kRwFileMode =
    S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRxFileMode =
    S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH;
}  // namespace

// Note: the use of shell commands for what would typically be regular stdlib
// filesystem io is because the installer does not have permissions in the
// /data/data/<app> directory and needs to utilize run-as.

void SwapCommand::ParseParameters(int argc, char** argv) {
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;
  if (!wrapper.Read(&data)) {
    return;
  }

  if (!request_.ParseFromString(data)) {
    return;
  }

  request_bytes_ = data;

  // Set this value here so we can re-use it in other methods.
  target_dir_ = "/data/data/" + request_.package_name() + "/.studio/";
  ready_to_run_ = true;
}

inline void FilterPids(std::vector<int>& process_ids,
                       proto::SwapRequest request) {
  process_ids.erase(
      remove_if(process_ids.begin(), process_ids.end(),
                [&](int x) {
                  return std::find(request.skip_process_ids().begin(),
                                   request.skip_process_ids().end(),
                                   x) != request.skip_process_ids().end();
                }),
      process_ids.end());
}

void SwapCommand::Run(Workspace& workspace) {
  Phase p("Command Swap");

  response_ = new proto::SwapResponse();
  workspace.GetResponse().set_allocated_swap_response(response_);
  LogEvent("Got swap request for:" + request_.package_name());

  if (!Setup(workspace)) {
    response_->set_status(proto::SwapResponse::ERROR);
    ErrEvent("Unable to setup workspace");
    return;
  }

  // Get the list of processes we need to attach to.
  std::vector<int> process_ids = GetApplicationPids();
  if (process_ids.empty()) {
    response_->set_status(proto::SwapResponse::ERROR);
    ErrEvent("No PIDs found.");
    return;
  }

  // Filter out PIDs that was instructed to skip.
  FilterPids(process_ids, request_);

  // Don't brother with the server if we have no work to do.
  if (process_ids.empty()) {
    response_->set_status(proto::SwapResponse::OK);
    LogEvent("No PIDs needs to be swapped");
    return;
  }

  // Start the server and wait for it to begin listening for connections.
  int read_fd, write_fd;
  int agent_server_pid;
  if (!WaitForServer(process_ids.size(), &agent_server_pid, &read_fd,
                     &write_fd)) {
    ErrEvent("Unable to start server");
    response_->set_status(proto::SwapResponse::ERROR);
    return;
  }

  OwnedMessagePipeWrapper server_input(write_fd);
  OwnedMessagePipeWrapper server_output(read_fd);

  if (!AttachAgents(process_ids)) {
    response_->set_status(proto::SwapResponse::ERROR);
    ErrEvent("One or more agents failed to attach");
    return;
  }

  if (!server_input.Write(request_bytes_)) {
    response_->set_status(proto::SwapResponse::ERROR);
    ErrEvent("Could not write to agent proxy server");
  }

  // TODO: This loop is at risk of hanging in a multi-agent scenario where
  // activity restart is required - if one agent dies before sending an
  // activity restart message, the server will never exit, as the still-alive
  // agents are permanently stuck waiting on activity restart and will not
  // close their sockets. Server should detect and send an agent death message
  // to the installer.

  std::string response_bytes;
  std::unordered_map<int, proto::AgentSwapResponse> agent_responses;
  auto overall_status = proto::AgentSwapResponse::UNKNOWN;
  while (agent_responses.size() < process_ids.size() &&
         server_output.Read(&response_bytes)) {
    proto::AgentSwapResponse agent_response;
    if (!agent_response.ParseFromString(response_bytes)) {
      response_->set_status(proto::SwapResponse::ERROR);
      ErrEvent("Received unparseable response from agent");
      return;
    }

    if (agent_responses.size() == 0) {
      overall_status = agent_response.status();
    } else if (agent_response.status() != overall_status) {
      overall_status = proto::AgentSwapResponse::ERROR;
    }

    agent_responses.emplace(agent_response.pid(), agent_response);

    // Convert proto events to events.
    for (int i = 0; i < agent_response.events_size(); i++) {
      const proto::Event& event = agent_response.events(i);
      AddRawEvent(ConvertProtoEventToEvent(event));
    }

    std::string jvmti_error_code = agent_response.jvmti_error_code();
    if (!jvmti_error_code.empty()) {
      response_->add_jvmti_error_code(jvmti_error_code);
    }
  }

  // Ensure all of the agents have responded.
  if (agent_responses.size() < process_ids.size()) {
    overall_status = proto::AgentSwapResponse::ERROR;
  }

  CmdCommand cmd;
  std::string output;
  std::string install_session = request_.session_id();
  // If the swap failed, revert the installation.
  if (overall_status != proto::AgentSwapResponse::OK) {
    cmd.AbortInstall(install_session, &output);
    response_->set_status(proto::SwapResponse::ERROR);
    return;
  }

  if (!cmd.CommitInstall(install_session, &output)) {
    response_->set_status(proto::SwapResponse::INSTALLATION_FAILED);
    return;
  }

  // Cleanup zombi agent-server status from the kernel.
  int status;
  waitpid(agent_server_pid, &status, 0);

  response_->set_status(proto::SwapResponse::OK);
  LogEvent("Swapped");
}

bool SwapCommand::Setup(const Workspace& workspace) noexcept {
  // Make sure the target dir exists.
  Phase p("Setup");
  std::string output;
  if (!RunCmd("mkdir", User::APP_PACKAGE, {"-p", target_dir_}, &output)) {
    ErrEvent("Could not create .studio directory");
    return false;
  }

  if (!CopyBinaries(workspace.GetTmpFolder(), target_dir_)) {
    ErrEvent("Could not copy binaries");
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
    LogEvent("Binaries already in data folder, skipping copy.");
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
    ErrEvent("Could not copy agent binary: "_s + cp_output);
    return false;
  }

  return true;
}

bool SwapCommand::WriteArrayToDisk(const unsigned char* array,
                                   uint64_t array_len,
                                   const std::string& dst_path) const noexcept {
  int fd = open(dst_path.c_str(), O_WRONLY | O_CREAT, kRwFileMode);
  if (fd == -1) {
    ErrEvent("WriteArrayToDisk, open: "_s + strerror(errno));
    return false;
  }
  int written = write(fd, array, array_len);
  if (written == -1) {
    ErrEvent("WriteArrayToDisk, write: "_s + strerror(errno));
    return false;
  }

  int close_result = close(fd);
  if (close_result == -1) {
    ErrEvent("WriteArrayToDisk, close: "_s + strerror(errno));
    return false;
  }

  chmod(dst_path.c_str(), kRxFileMode);
  return true;
}

std::vector<int> SwapCommand::GetApplicationPids() const {
  Phase p("GetApplicationPids");
  std::vector<int> process_ids;
  std::string pidof_output;
  std::vector<std::string> process_names(request_.process_names().begin(),
                                         request_.process_names().end());

  if (!RunCmd("pidof", User::SHELL_USER, {process_names}, &pidof_output)) {
    ErrEvent("Could not get app pid for package: "_s + request_.package_name());
    return process_ids;
  }

  int pid;
  std::istringstream pids(pidof_output);
  while (pids >> pid) {
    process_ids.push_back(pid);
  }

  return process_ids;
}

bool SwapCommand::WaitForServer(int agent_count, int* server_pid, int* read_fd,
                                int* write_fd) const {
  Phase p("WaitForServer");

  int sync_pipe[2];
  if (pipe(sync_pipe) != 0) {
    ErrEvent("Could not create sync pipe");
    return false;
  }

  const int sync_read_fd = sync_pipe[0];
  const int sync_write_fd = sync_pipe[1];

  // The server doesn't know about the read end of the pipe, so set it to
  // close-on-exec. Not a big deal if this fails, but we should still log.
  if (fcntl(sync_read_fd, F_SETFD, FD_CLOEXEC) == -1) {
    LogEvent("Could not set sync pipe read end to close-on-exec");
  }

  std::string command = target_dir_ + kServerFilename;

  std::vector<std::string> parameters;
  parameters.push_back(to_string(agent_count));
  parameters.push_back(Socket::kDefaultAddress);

  // Pass the write end of the sync pipe to the server. The server will close
  // the pipe to indicate that it is ready to receive connections.
  parameters.push_back(to_string(sync_write_fd));
  LogEvent(parameters.back());

  int err_fd = -1;
  bool success =
      Executor::ForkAndExecAs(command, request_.package_name(), parameters,
                              write_fd, read_fd, &err_fd, server_pid);
  close(sync_write_fd);
  close(err_fd);

  if (success) {
    // This branch is only possible in the parent process; we only reach this
    // conditional in the child if success is false (the server failed to run).
    char unused;
    if (read(sync_read_fd, &unused, 1) != 0) {
      ErrEvent("Unexpected response received on sync pipe");
    }
  }

  close(sync_read_fd);
  return success;
}

bool SwapCommand::AttachAgents(const std::vector<int>& process_ids) const {
  Phase p("AttachAgents");
  CmdCommand cmd;
  for (int pid : process_ids) {
    std::string output;
    if (!cmd.AttachAgent(pid, target_dir_ + kAgentFilename,
                         {Socket::kDefaultAddress}, &output)) {
      ErrEvent("Could not attach agent to process: "_s + output);
      return false;
    }
  }

  return true;
}

bool SwapCommand::RunCmd(const std::string& shell_cmd, User run_as,
                         const std::vector<std::string>& args,
                         std::string* output) const {
  std::string err;
  if (run_as == User::APP_PACKAGE) {
    return Executor::RunAs(shell_cmd, request_.package_name(), args, output,
                           &err);
  } else {
    return Executor::Run(shell_cmd, args, output, &err);
  }
}

}  // namespace deploy
