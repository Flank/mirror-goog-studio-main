/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "tools/base/deploy/installer/live_literal_update.h"

#include <fcntl.h>
#include <sys/wait.h>

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/server/app_servers.h"

namespace deploy {

void LiveLiteralUpdateCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_live_literal_request()) {
    return;
  }

  request_ = request.live_literal_request();
  package_name_ = request_.package_name();

  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  process_ids_ = pids;
  extra_agents_count_ = request_.extra_agents();
  ready_to_run_ = true;
}

void LiveLiteralUpdateCommand::Run(proto::InstallerResponse* response) {
  if (!PrepareInteraction(request_.arch())) {
    ErrEvent("Unable to prepare interaction");
    return;
  }

  proto::LiveLiteralUpdateResponse* update_response =
      response->mutable_live_literal_response();

  Update(request_, update_response);

  // Do this even if the deployment failed; it's retrieving data unrelated to
  // the current deployment. We might want to find a better time to do this.
  auto logsResp = GetAgentLogs();
  if (!logsResp) {
    Log::W("Could not write to server to retrieve agent logs.");
    return;
  }

  for (const auto& log : logsResp->logs()) {
    auto added = update_response->add_agent_logs();
    *added = log;
  }
}

// TODO: Refactor this which is mostly identical to OverlaySwapCommand::Swap()
void LiveLiteralUpdateCommand::Update(
    const proto::LiveLiteralUpdateRequest& request,
    proto::LiveLiteralUpdateResponse* response) {
  Phase p("LiveLiteralUpdate");
  if (response->status() != proto::LiveLiteralUpdateResponse::UNKNOWN) {
    return;
  }

  // Remove process ids that we do not need to swap.
  FilterProcessIds(&process_ids_);

  // Don't bother with the server if we have no work to do.
  if (process_ids_.empty() && extra_agents_count_ == 0) {
    LogEvent("No PIDs needs to be update Live Literal");
    response->set_status(proto::LiveLiteralUpdateResponse::OK);
    return;
  }

  // Request for the install-server to open a socket and begin listening for
  // agents to connect. Agents connect shortly after they are attached (below).
  auto listen_resp = ListenForAgents();
  if (listen_resp == nullptr) {
    response->set_status(
        proto::LiveLiteralUpdateResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  if (listen_resp->status() != proto::OpenAgentSocketResponse::OK) {
    response->set_status(
        proto::LiveLiteralUpdateResponse::READY_FOR_AGENTS_NOT_RECEIVED);
    return;
  }

  if (!Attach(process_ids_)) {
    response->set_status(proto::LiveLiteralUpdateResponse::AGENT_ATTACH_FAILED);
    return;
  }

  proto::SendAgentMessageRequest req;
  req.set_agent_count(process_ids_.size() + extra_agents_count_);
  *req.mutable_agent_request()->mutable_live_literal_request() = request;

  auto resp = client_->SendAgentMessage(req);
  if (!resp) {
    response->set_status(
        proto::LiveLiteralUpdateResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() != proto::AgentResponse::OK) {
      auto failed_agent = response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  if (resp->status() == proto::SendAgentMessageResponse::OK) {
    if (response->failed_agents_size() == 0) {
      response->set_status(proto::LiveLiteralUpdateResponse::OK);
    } else {
      response->set_status(proto::LiveLiteralUpdateResponse::AGENT_ERROR);
    }
    return;
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(package_name_, &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        response->set_status(
            proto::LiveLiteralUpdateResponse::PROCESS_CRASHING);
        response->set_extra(record.process_name);
        return;
      }

      if (record.not_responding) {
        response->set_status(
            proto::LiveLiteralUpdateResponse::PROCESS_NOT_RESPONDING);
        response->set_extra(record.process_name);
        return;
      }
    }
  }

  for (int pid : request.process_ids()) {
    const std::string pid_string = to_string(pid);
    if (IO::access("/proc/" + pid_string, F_OK) != 0) {
      response->set_status(
          proto::LiveLiteralUpdateResponse::PROCESS_TERMINATED);
      response->set_extra(pid_string);
      return;
    }
  }

  response->set_status(
      proto::LiveLiteralUpdateResponse::MISSING_AGENT_RESPONSES);
}

}  // namespace deploy
