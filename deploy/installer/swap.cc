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
#include "tools/base/deploy/installer/server/app_servers.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

// Note: the use of shell commands for what would typically be regular stdlib
// filesystem io is because the installer does not have permissions in the
// /data/data/<app> directory and needs to utilize run-as.

void SwapCommand::ParseParameters(const proto::InstallerRequest& request) {
  if (!request.has_swap_request()) {
    return;
  }

  request_ = request.swap_request();

  // Set this value here so we can re-use it in other methods.
  const std::string& pkg = request_.package_name();
  target_dir_ = Sites::AppStudio(pkg);
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

  if (!CopyBinaries()) {
    ErrEvent("Could not copy binaries");
    return false;
  }

  client_ = AppServers::Get(request_.package_name(), workspace_.GetTmpFolder(),
                            workspace_.GetVersion());
  return true;
}

bool SwapCommand::CopyBinaries() const noexcept {
  Phase p("CopyBinaries");

  // Extract binaries from matryoshka to the tmp folder.
  const std::string tmp_dir = workspace_.GetTmpFolder();
  std::vector<std::string> to_extract = {kAgent, kInstallServer};
#if defined(__aarch64__) || defined(__x86_64__)
  to_extract.emplace_back(kAgentAlt);
#endif
  ExtractBinaries(workspace_.GetTmpFolder(), to_extract);

  // Copy binaries from tmp folder to app world.
  std::string pkg = request_.package_name();
  const std::string dst_dir = Sites::AppStudio(pkg);

  std::string cp_output;
  if (!RunCmd("cp", User::APP_PACKAGE, {"-rF", tmp_dir, dst_dir}, &cp_output)) {
    cp_output.clear();
    // We don't need to check the output of this. It will fail if the code_cache
    // already exists; if the code_cache doesn't exist and we can't create it,
    // that failure will be caught when we try to copy the binaries.
    RunCmd("mkdir", User::APP_PACKAGE, {dst_dir}, nullptr);
    if (!RunCmd("cp", User::APP_PACKAGE, {"-rF", tmp_dir, dst_dir},
                &cp_output)) {
      ErrEvent("Could not copy agent binary: "_s + cp_output);
      return false;
    }
  }

  return true;
}

proto::SwapResponse::Status SwapCommand::Swap() {
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

  // Attach agents to pids.
  std::string agent_path = target_dir_ + kAgent;
#if defined(__aarch64__) || defined(__x86_64__)
  if (request_.arch() == proto::ARCH_32_BIT) {
    agent_path = target_dir_ + kAgentAlt;
  }
#endif
  if (!Attach(request_.process_ids(), agent_path)) {
    ErrEvent("Could not attach agents");
    return proto::SwapResponse::AGENT_ATTACH_FAILED;
  }

  size_t total_agents = request_.process_ids().size() + request_.extra_agents();

  proto::SendAgentMessageRequest send_request;
  send_request.set_agent_count(total_agents);
  *send_request.mutable_agent_request()->mutable_swap_request() = request_;

  auto resp = client_->SendAgentMessage(send_request);
  if (!resp) {
    ErrEvent("Could not send to install server");
    return proto::SwapResponse::INSTALL_SERVER_COM_ERR;
  }

  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() != proto::AgentResponse::OK) {
      auto failed_agent = response_->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  // Ensure all of the agents have responded.
  if (resp->agent_responses_size() == total_agents) {
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

bool SwapCommand::WaitForServer() {
  Phase p("WaitForServer");

  proto::OpenAgentSocketRequest socket_request;
  socket_request.set_socket_name(GetSocketName());

  auto resp = client_->OpenAgentSocket(socket_request);
  if (!resp) {
    ErrEvent("SwapCommand::WaitForServer: Error with install-server");
    return false;
  }

  if (resp->status() != proto::OpenAgentSocketResponse::OK) {
    ErrEvent("SwapCommand::WaitForServer: Bad response from install-server");
    return false;
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
