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
  package_name_ = request_.package_name();

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

  // Attach agents to pids.
  if (!PrepareInteraction(request_.arch())) {
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

proto::SwapResponse::Status SwapCommand::Swap() {
  // Don't bother with the server if we have no work to do.
  if (request_.process_ids().empty() && request_.extra_agents() <= 0) {
    LogEvent("No PIDs needs to be swapped");
    return proto::SwapResponse::OK;
  }

  // Start the server and wait for it to begin listening for connections.
  auto listen_resp = ListenForAgents();
  if (listen_resp == nullptr) {
    ErrEvent("ListenForAgents() No response from app-server");
    return proto::SwapResponse::START_SERVER_FAILED;
  }

  if (listen_resp->status() != proto::OpenAgentSocketResponse::OK) {
    ErrEvent("ListenForAgents: No OK response (" +
             to_string(listen_resp->status()) + ")");
    return proto::SwapResponse::START_SERVER_FAILED;
  }

  if (!Attach(request_.process_ids())) {
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
}  // namespace deploy
