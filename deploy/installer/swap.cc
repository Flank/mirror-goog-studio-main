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
#include "tools/base/deploy/common/proto_pipe.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"

namespace deploy {

namespace {
const std::string kAgentFilename = "agent.so";
const std::string kAgentAltFilename = "agent-alt.so";
const std::string kServerFilename = "install_server";
}  // namespace

// Note: the use of shell commands for what would typically be regular stdlib
// filesystem io is because the installer does not have permissions in the
// /data/data/<app> directory and needs to utilize run-as.

void SwapCommand::ParseParameters(const proto::InstallerRequest& request) {
  if (!request.has_swap_request()) {
    return;
  }

  request_ = request.swap_request();

  // Set this value here so we can re-use it in other methods.
  target_dir_ =
      "/data/data/" + request_.package_name() + "/code_cache/.studio/";
  ready_to_run_ = true;
}

void SwapCommand::Run(proto::InstallerResponse* response) {
  Phase p("Command Swap");

  response_ = response->mutable_swap_response();
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
    cmd.AbortInstall(install_session, &output);
    response_->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Unable to setup workspace");
    return;
  }

  proto::SwapResponse::Status swap_status = Swap();

  proto::InstallServerResponse server_response;
  client_->KillServerAndWait(&server_response);
  ConvertProtoEventsToEvents(server_response.events());

  // If the swap fails, abort the installation.
  if (swap_status != proto::SwapResponse::OK) {
    cmd.AbortInstall(install_session, &output);
    response_->set_status(swap_status);
    return;
  }

  // If the swap succeeds but the commit fails, report a failed install.
  if (!cmd.CommitInstall(install_session, &output)) {
    ErrEvent("Swap could not commit install");
    ErrEvent(output);
    response_->set_status(proto::SwapResponse::INSTALLATION_FAILED);
    return;
  }

  LogEvent("Successfully installed package: " + request_.package_name());
  response_->set_status(proto::SwapResponse::OK);
}

bool SwapCommand::Setup() noexcept {
  // Make sure the target dir exists.
  Phase p("Setup");

  std::string code_cache =
      "/data/data/" + request_.package_name() + "/code_cache/";
  if (!CopyBinaries(workspace_.GetTmpFolder(), code_cache)) {
    ErrEvent("Could not copy binaries");
    return false;
  }

  client_ = StartInstallServer(
      Executor::Get(), workspace_.GetTmpFolder() + kServerFilename,
      request_.package_name(), kServerFilename + "-" + workspace_.GetVersion());

  return true;
}

bool SwapCommand::CopyBinaries(const std::string& src_path,
                               const std::string& dst_path) const noexcept {
  Phase p("CopyBinaries");
  std::string agent_src_path = src_path + kAgentFilename;
  std::string agent_alt_src_path = src_path + kAgentAltFilename;
  std::string server_src_path = src_path + kServerFilename;

  bool need_agent = IO::access(agent_src_path, F_OK) == -1;
  bool need_server = IO::access(server_src_path, F_OK) == -1;

#if defined(__aarch64__) || defined(__x86_64__)
  bool need_agent_alt = IO::access(agent_alt_src_path, F_OK) == -1;
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
    if (!WriteArrayToDisk(agent->content, agent->content_len, agent_src_path)) {
      ErrEvent("Failed to write agent.so");
      return false;
    }
  }

  if (need_agent_alt) {
    matryoshka::Doll* agent_alt = matryoshka::FindByName(dolls, "agent-alt.so");
    if (!agent_alt) {
      ErrEvent("Installer binary does not contain agent-alt.so");
      return false;
    }
    if (!WriteArrayToDisk(agent_alt->content, agent_alt->content_len,
                          agent_alt_src_path)) {
      ErrEvent("Failed to write agent-alt.so");
      return false;
    }
  }

  if (need_server) {
    matryoshka::Doll* install_server =
        matryoshka::FindByName(dolls, kServerFilename);
    if (!install_server) {
      ErrEvent("Installer binary does not contain install server");
      return false;
    }
    if (!WriteArrayToDisk(install_server->content, install_server->content_len,
                          server_src_path)) {
      ErrEvent("Failed to write agent_server");
      return false;
    }
  }

  std::string cp_output;

  const std::string studio_dir = dst_path + ".studio/";
  if (!RunCmd("cp", User::APP_PACKAGE, {"-rF", src_path, studio_dir},
              &cp_output)) {
    cp_output.clear();
    // We don't need to check the output of this. It will fail if the code_cache
    // already exists; if the code_cache doesn't exist and we can't create it,
    // that failure will be caught when we try to copy the binaries.
    RunCmd("mkdir", User::APP_PACKAGE, {dst_path}, nullptr);
    if (!RunCmd("cp", User::APP_PACKAGE, {"-rF", src_path, studio_dir},
                &cp_output)) {
      ErrEvent("Could not copy agent binary: "_s + cp_output);
      return false;
    }
  }

  return true;
}

proto::SwapResponse::Status SwapCommand::Swap() const {
  // Don't bother with the server if we have no work to do.
  if (request_.process_ids().empty() && request_.extra_agents() <= 0) {
    LogEvent("No PIDs needs to be swapped");
    return proto::SwapResponse::OK;
  }
  // Start the server and wait for it to begin listening for connections.
  if (!WaitForServer()) {
    ErrEvent("Unable to start server");
    return proto::SwapResponse::START_SERVER_FAILED;
  }

  proto::InstallServerRequest server_request;
  server_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);

  proto::InstallServerResponse server_response;

  if (!AttachAgents()) {
    ErrEvent("Could not attach agents");
    client_->KillServerAndWait(&server_response);
    return proto::SwapResponse::AGENT_ATTACH_FAILED;
  }

  size_t total_agents = request_.process_ids().size() + request_.extra_agents();

  auto send_request = server_request.mutable_send_request();
  send_request->set_agent_count(total_agents);
  *send_request->mutable_agent_request()->mutable_swap_request() = request_;
  if (!client_->Write(server_request)) {
    ErrEvent("Could not write to install server");
    client_->KillServerAndWait(&server_response);
    return proto::SwapResponse::WRITE_TO_SERVER_FAILED;
  }

  if (!client_->Read(&server_response)) {
    ErrEvent("Could not read from install server");
    client_->KillServerAndWait(&server_response);
    return proto::SwapResponse::READ_FROM_SERVER_FAILED;
  }

  for (const auto& agent_response :
       server_response.send_response().agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() != proto::AgentResponse::OK) {
      auto failed_agent = response_->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  // Ensure all of the agents have responded.
  if (server_response.send_response().agent_responses_size() == total_agents) {
    return response_->failed_agents_size() == 0
               ? proto::SwapResponse::OK
               : proto::SwapResponse::AGENT_ERROR;
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(request_.package_name(), &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        response_->set_extra(record.process_name);
        return proto::SwapResponse::PROCESS_CRASHING;
      }

      if (record.not_responding) {
        response_->set_extra(record.process_name);
        return proto::SwapResponse::PROCESS_NOT_RESPONDING;
      }
    }
  }

  for (int pid : request_.process_ids()) {
    const std::string pid_string = to_string(pid);
    if (IO::access("/proc/" + pid_string, F_OK) != 0) {
      response_->set_extra(pid_string);
      return proto::SwapResponse::PROCESS_TERMINATED;
    }
  }

  return proto::SwapResponse::MISSING_AGENT_RESPONSES;
}

bool SwapCommand::WaitForServer() const {
  Phase p("WaitForServer");
  proto::InstallServerRequest server_request;
  server_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);

  auto socket_request = server_request.mutable_socket_request();
  socket_request->set_socket_name(Socket::kDefaultAddress);

  proto::InstallServerResponse server_response;
  if (!client_->Write(server_request)) {
    ErrEvent("SwapCommand::WaitForServer: Error writing to install-server");
    return false;
  }

  if (!client_->Read(&server_response)) {
    ErrEvent("SwapCommand::WaitForServer: Error reading from install-server");
    return false;
  }

  if (server_response.status() !=
          proto::InstallServerResponse::REQUEST_COMPLETED ||
      server_response.socket_response().status() !=
          proto::OpenAgentSocketResponse::OK) {
    ErrEvent(
        "SwapCommand::WaitForServer: Unexpected response from install-server");
    return false;
  }

  return true;
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
  Executor* executor = &Executor::Get();
  RunasExecutor alt(request_.package_name(), *executor);
  if (run_as == User::APP_PACKAGE) {
    executor = &alt;
  }
  return executor->Run(shell_cmd, args, output, &err);
}

}  // namespace deploy
