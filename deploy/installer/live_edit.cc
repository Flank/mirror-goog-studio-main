/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "tools/base/deploy/installer/live_edit.h"

#include "tools/base/deploy/installer/binary_extract.h"

namespace deploy {

void LiveEditCommand::ParseParameters(const proto::InstallerRequest& request) {
  if (!request.has_le_request()) {
    return;
  }

  request_ = request.le_request();
  package_name_ = request_.package_name();
  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  process_ids_ = pids;
  ready_to_run_ = true;
}

void LiveEditCommand::Run(proto::InstallerResponse* response) {
  proto::LiveEditResponse* le_response = response->mutable_le_response();

  if (!PrepareInteraction(request_.arch())) {
    ErrEvent("Unable to prepare interaction");
    return;
  }

  auto listen_response = ListenForAgents();
  if (listen_response == nullptr) {
    le_response->set_status(proto::LiveEditResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  if (listen_response->status() != proto::OpenAgentSocketResponse::OK) {
    le_response->set_status(
        proto::LiveEditResponse::READY_FOR_AGENTS_NOT_RECEIVED);
    return;
  }

  if (!Attach(process_ids_)) {
    le_response->set_status(proto::LiveEditResponse::AGENT_ATTACH_FAILED);
    return;
  }

  // Send le request to agents
  proto::SendAgentMessageRequest req;
  req.set_agent_count(process_ids_.size());
  *req.mutable_agent_request()->mutable_le_request() = request_;
  auto resp = client_->SendAgentMessage(req);
  if (!resp) {
    le_response->set_status(proto::LiveEditResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  // Retrieve foreign processes events
  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() == proto::AgentResponse::OK) {
      auto success_agent = le_response->add_success_agents();
      *success_agent = agent_response;
    } else {
      auto failed_agent = le_response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  if (resp->status() == proto::SendAgentMessageResponse::OK) {
    if (le_response->failed_agents_size() == 0) {
      le_response->set_status(proto::LiveEditResponse::OK);
    } else {
      le_response->set_status(proto::LiveEditResponse::AGENT_ERROR);
    }
  }
}

}  // namespace deploy
