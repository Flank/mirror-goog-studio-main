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

#include <fcntl.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/installer/runas_executor.h"

namespace deploy {

namespace {
const std::string kAgentFilename = "agent.so";
const std::string kAgentAltFilename = "agent-alt.so";
const std::string kServerFilename = "server.so";
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
  target_dir_ =
      "/data/data/" + request_.package_name() + "/code_cache/.studio/";
  ready_to_run_ = true;
}

void SwapCommand::Run() {
  Phase p("Command Swap");

  response_ = new proto::SwapResponse();
  workspace_.GetResponse().set_allocated_swap_response(response_);
  std::string install_session = request_.session_id();
  CmdCommand cmd(workspace_);
  std::string output;

  if (install_session.compare("<SKIPPED-INSTALLATION>") == 0) {
    if (request_.restart_activity() &&
        !cmd.UpdateAppInfo("all", request_.package_name(), &output)) {
      response_->set_status(proto::SwapResponse::ACTIVITY_RESTART_FAILED);
    } else {
      response_->set_status(proto::SwapResponse::OK);
    }
    return;
  }

  LogEvent("Got swap request for:" + request_.package_name());

  if (!Setup()) {
    response_->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Unable to setup workspace");
    return;
  }

  proto::SwapResponse::Status swap_status = Swap();

  // If the swap fails, abort the installation.
  if (swap_status != proto::SwapResponse::OK) {
    cmd.AbortInstall(install_session, &output);
    response_->set_status(swap_status);
    return;
  }

  // If the swap succeeds but the commit fails, report a failed install.
  if (!cmd.CommitInstall(install_session, &output)) {
    response_->set_status(proto::SwapResponse::INSTALLATION_FAILED);
    return;
  }

  LogEvent("Successfully installed package: " + request_.package_name());
  response_->set_status(proto::SwapResponse::OK);
}

bool SwapCommand::Setup() noexcept {
  // Make sure the target dir exists.
  Phase p("Setup");

  if (!CopyBinaries(workspace_.GetTmpFolder(), target_dir_)) {
    ErrEvent("Could not copy binaries");
    return false;
  }

  return true;
}

bool SwapCommand::CopyBinaries(const std::string& src_path,
                               const std::string& dst_path) const noexcept {
  Phase p("CopyBinaries");
  // TODO: Cleanup previous version of the binaries?
  std::string agent_src_path = src_path + kAgentFilename;
  std::string agent_alt_src_path = src_path + kAgentAltFilename;
  std::string server_src_path = src_path + kServerFilename;

  bool need_agent = access(agent_src_path.c_str(), F_OK) == -1;
  bool need_server = access(server_src_path.c_str(), F_OK) == -1;

#if defined(__aarch64__) || defined(__x86_64__)
  bool need_agent_alt = access(agent_alt_src_path.c_str(), F_OK) == -1;
#else
  bool need_agent_alt = false;
#endif

  std::vector<std::unique_ptr<matryoshka::Doll>> dolls;
  if (need_agent || need_agent_alt || need_server) {
    if (!matryoshka::Open(dolls)) {
      ErrEvent("Installer binary does not contain any agent binaries.");
      return false;
    }

    for (auto& doll : dolls) {
      LogEvent("Matryoshka binary found:" + doll->name);
    }
  }

  if (need_agent) {
    matryoshka::Doll* agent = matryoshka::FindByName(dolls, "agent.so");
    if (!agent) {
      ErrEvent("Installer binary does not contain agent.so");
      return false;
    }
    WriteArrayToDisk(agent->content, agent->content_len, agent_src_path);
  }

  if (need_agent_alt) {
    matryoshka::Doll* agent_alt = matryoshka::FindByName(dolls, "agent-alt.so");
    if (!agent_alt) {
      ErrEvent("Installer binary does not contain agent-alt.so");
      return false;
    }
    WriteArrayToDisk(agent_alt->content, agent_alt->content_len,
                     agent_alt_src_path);
  }

  if (need_server) {
    matryoshka::Doll* agent_server =
        matryoshka::FindByName(dolls, "agent_server");
    if (!agent_server) {
      ErrEvent("Installer binary does not contain agent_server");
      return false;
    }

    WriteArrayToDisk(agent_server->content, agent_server->content_len,
                     server_src_path);
  }

  std::string cp_output;
  if (!RunCmd("cp", User::APP_PACKAGE, {"-rF", src_path, dst_path},
              &cp_output)) {
    ErrEvent("Could not copy agent binary: "_s + cp_output);
    return false;
  }

  return true;
}

bool SwapCommand::WriteArrayToDisk(const unsigned char* array,
                                   uint64_t array_len,
                                   const std::string& dst_path) const noexcept {
  Phase p("WriteArrayToDisk");
  std::string real_path = workspace_.GetRoot() + dst_path;
  int fd = open(real_path.c_str(), O_WRONLY | O_CREAT, kRwFileMode);
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

  chmod(real_path.c_str(), kRxFileMode);
  return true;
}

proto::SwapResponse::Status SwapCommand::Swap() const {
  // Don't bother with the server if we have no work to do.
  if (request_.process_ids().empty() && request_.extra_agents() <= 0) {
    LogEvent("No PIDs needs to be swapped");
    return proto::SwapResponse::OK;
  }
  // Start the server and wait for it to begin listening for connections.
  int read_fd, write_fd, agent_server_pid, status;
  if (!WaitForServer(request_.process_ids().size() + request_.extra_agents(),
                     &agent_server_pid, &read_fd, &write_fd)) {
    ErrEvent("Unable to start server");
    return proto::SwapResponse::START_SERVER_FAILED;
  }

  OwnedMessagePipeWrapper server_input(write_fd);
  OwnedMessagePipeWrapper server_output(read_fd);

  if (!AttachAgents()) {
    ErrEvent("Could not attach agents");
    waitpid(agent_server_pid, &status, 0);
    return proto::SwapResponse::AGENT_ATTACH_FAILED;
  }

  if (!server_input.Write(request_bytes_)) {
    ErrEvent("Could not write to agent proxy server");
    waitpid(agent_server_pid, &status, 0);
    return proto::SwapResponse::WRITE_TO_SERVER_FAILED;
  }

  size_t total_agents = request_.process_ids().size() + request_.extra_agents();

  std::string response_bytes;
  std::unordered_map<int, proto::AgentSwapResponse> agent_responses;

  while (agent_responses.size() < total_agents &&
         server_output.Read(&response_bytes)) {
    proto::AgentSwapResponse agent_response;

    if (!agent_response.ParseFromString(response_bytes)) {
      waitpid(agent_server_pid, &status, 0);
      return proto::SwapResponse::UNPARSEABLE_AGENT_RESPONSE;
    }

    agent_responses.emplace(agent_response.pid(), agent_response);

    // Convert proto events to events.
    for (int i = 0; i < agent_response.events_size(); i++) {
      const proto::Event& event = agent_response.events(i);
      AddRawEvent(ConvertProtoEventToEvent(event));
    }

    if (agent_response.status() != proto::AgentSwapResponse::OK) {
      auto failed_agent = response_->add_failed_agents();
      *failed_agent = agent_response;
    }
  }
  // Cleanup zombie agent-server status from the kernel.
  waitpid(agent_server_pid, &status, 0);

  // Ensure all of the agents have responded.
  if (agent_responses.size() < total_agents) {
    return proto::SwapResponse::MISSING_AGENT_RESPONSES;
  }

  if (response_->failed_agents_size() > 0) {
    return proto::SwapResponse::AGENT_ERROR;
  }

  return proto::SwapResponse::OK;
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

  std::vector<std::string> parameters;
  parameters.push_back(to_string(agent_count));
  parameters.push_back(Socket::kDefaultAddress);

  // Pass the write end of the sync pipe to the server. The server will close
  // the pipe to indicate that it is ready to receive connections.
  parameters.push_back(to_string(sync_write_fd));
  LogEvent(parameters.back());

  int err_fd = -1;
  RunasExecutor run_as(request_.package_name(), workspace_.GetExecutor());
  bool success = run_as.ForkAndExec(target_dir_ + kServerFilename, parameters,
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

bool SwapCommand::AttachAgents() const {
  Phase p("AttachAgents");
  CmdCommand cmd(workspace_);
  for (int pid : request_.process_ids()) {
    std::string output;
    std::string agent = kAgentFilename;
#if defined(__aarch64__) || defined(__x86_64__)
    if (request_.arch() == proto::ARCH_32_BIT) {
      agent = kAgentAltFilename;
    }
#endif
    LogEvent("Attaching agent: '"_s + agent + "'");
    output = "";
    if (!cmd.AttachAgent(pid, target_dir_ + agent, {Socket::kDefaultAddress},
                         &output)) {
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
  Executor* executor = &workspace_.GetExecutor();
  RunasExecutor alt(request_.package_name(), workspace_.GetExecutor());
  if (run_as == User::APP_PACKAGE) {
    executor = &alt;
  }
  return executor->Run(shell_cmd, args, output, &err);
}

}  // namespace deploy
