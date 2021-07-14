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

#include "tools/base/deploy/installer/overlay_swap.h"

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

void OverlaySwapCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_overlay_swap_request()) {
    return;
  }

  request_ = request.overlay_swap_request();
  package_name_ = request_.package_name();

  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  process_ids_ = pids;
  extra_agents_count_ = request_.extra_agents();
  ready_to_run_ = true;
}

void OverlaySwapCommand::Run(proto::InstallerResponse* response) {
  proto::SwapResponse* swap_response = response->mutable_swap_response();

  if (!PrepareInteraction(request_.arch())) {
    ErrEvent("Unable to prepare interaction");
    return;
  }

  std::unique_ptr<proto::SwapRequest> request = PrepareAndBuildRequest();
  if (request == nullptr) {
    swap_response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("OverlaySwap: Unable to PrepareAndBuildRequest");
    return;
  }

  Swap(std::move(request), swap_response);
  ProcessResponse(swap_response);
}

bool OverlaySwapCommand::Swap(
    const std::unique_ptr<proto::SwapRequest> swap_request,
    proto::SwapResponse* swap_response) {
  Phase p("Swap");
  if (swap_response->status() != proto::SwapResponse::UNKNOWN) {
    ErrEvent("OverlaySwap: Unable to Swap (swapResponse status is populated)");
    return false;
  }

  // Remove process ids that we do not need to swap.
  FilterProcessIds(&process_ids_);

  if (process_ids_.empty() && extra_agents_count_ == 0) {
    LogEvent("No PIDs needs to be swapped");
    swap_response->set_status(proto::SwapResponse::OK);
    return true;
  }

  // Request for the install-server to open a socket and begin listening for
  // agents to connect. Agents connect shortly after they are attached (below).
  auto listen_resp = ListenForAgents();
  if (listen_resp == nullptr) {
    swap_response->set_status(proto::SwapResponse::INSTALL_SERVER_COM_ERR);
    return false;
  }

  if (listen_resp->status() != proto::OpenAgentSocketResponse::OK) {
    swap_response->set_status(
        proto::SwapResponse::READY_FOR_AGENTS_NOT_RECEIVED);
    return false;
  }

  if (!Attach(process_ids_)) {
    ErrEvent("Unable to Attach");
    swap_response->set_status(proto::SwapResponse::AGENT_ATTACH_FAILED);
    return false;
  }

  proto::SendAgentMessageRequest req;
  req.set_agent_count(process_ids_.size() + extra_agents_count_);
  *req.mutable_agent_request()->mutable_swap_request() = *swap_request.get();

  auto resp = client_->SendAgentMessage(req);
  if (!resp) {
    swap_response->set_status(proto::SwapResponse::INSTALL_SERVER_COM_ERR);
    return false;
  }

  for (const auto& agent_response : resp->agent_responses()) {
    ConvertProtoEventsToEvents(agent_response.events());
    if (agent_response.status() != proto::AgentResponse::OK) {
      auto failed_agent = swap_response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  if (resp->status() == proto::SendAgentMessageResponse::OK) {
    if (swap_response->failed_agents_size() == 0) {
      swap_response->set_status(proto::SwapResponse::OK);
      return true;
    } else {
      swap_response->set_status(proto::SwapResponse::AGENT_ERROR);
      return false;
    }
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(package_name_, &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        swap_response->set_status(proto::SwapResponse::PROCESS_CRASHING);
        swap_response->set_extra(record.process_name);
        return false;
      }

      if (record.not_responding) {
        swap_response->set_status(proto::SwapResponse::PROCESS_NOT_RESPONDING);
        swap_response->set_extra(record.process_name);
        return false;
      }
    }
  }

  for (int pid : swap_request->process_ids()) {
    const std::string pid_string = to_string(pid);
    if (IO::access("/proc/" + pid_string, F_OK) != 0) {
      swap_response->set_status(proto::SwapResponse::PROCESS_TERMINATED);
      swap_response->set_extra(pid_string);
      return false;
    }
  }

  swap_response->set_status(proto::SwapResponse::MISSING_AGENT_RESPONSES);
  return false;
}

std::unique_ptr<proto::SwapRequest>
OverlaySwapCommand::PrepareAndBuildRequest() {
  Phase p("PreSwap");
  std::unique_ptr<proto::SwapRequest> request(new proto::SwapRequest());

  for (auto& clazz : request_.new_classes()) {
    request->add_new_classes()->CopyFrom(clazz);
  }

  for (auto& clazz : request_.modified_classes()) {
    request->add_modified_classes()->CopyFrom(clazz);
  }

  request->set_package_name(package_name_);
  request->set_restart_activity(request_.restart_activity());
  request->set_structural_redefinition(request_.structural_redefinition());
  request->set_variable_reinitialization(request_.variable_reinitialization());
  request->set_overlay_swap(true);
  return request;
}

void OverlaySwapCommand::BuildOverlayUpdateRequest(
    proto::OverlayUpdateRequest* request) {
  request->set_overlay_id(request_.overlay_id());
  request->set_expected_overlay_id(request_.expected_overlay_id());

  const std::string pkg = request_.package_name();
  request->set_package_name(pkg);

  const std::string overlay_path = Sites::AppOverlays(pkg);
  request->set_overlay_path(overlay_path);

  for (auto clazz : request_.new_classes()) {
    auto file = request->add_files_to_write();
    file->set_path(clazz.name() + ".dex");
    file->set_allocated_content(clazz.release_dex());
  }

  for (auto clazz : request_.modified_classes()) {
    auto file = request->add_files_to_write();
    file->set_path(clazz.name() + ".dex");
    file->set_allocated_content(clazz.release_dex());
  }

  for (auto resource : request_.resource_overlays()) {
    auto file = request->add_files_to_write();
    file->set_path(resource.path());
    file->set_allocated_content(resource.release_content());
  }
}

void OverlaySwapCommand::ProcessResponse(proto::SwapResponse* response) {
  Phase p("PostSwap");

  if (response->status() == proto::SwapResponse::OK ||
      request_.always_update_overlay()) {
    UpdateOverlay(response);
  }

  // Do this even if the deployment failed; it's retrieving data unrelated to
  // the current deployment. We might want to find a better time to do this.
  auto logsResp = GetAgentLogs();
  if (!logsResp) {
    return;
  }

  for (const auto& log : logsResp->logs()) {
    auto added = response->add_agent_logs();
    *added = log;
  }
}

void OverlaySwapCommand::UpdateOverlay(proto::SwapResponse* response) {
  Phase p("UpdateOverlay");

  bool swap_failed = (response->status() != proto::SwapResponse::OK);

  proto::OverlayUpdateRequest req;
  BuildOverlayUpdateRequest(&req);

  auto resp = client_->UpdateOverlay(req);
  if (!resp) {
    response->set_status(proto::SwapResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  response->set_status(OverlayStatusToSwapStatus(resp->status()));
  response->set_extra(resp->error_message());

  bool should_restart = request_.restart_activity() &&
                        response->status() == proto::SwapResponse::OK;

  CmdCommand cmd(workspace_);
  std::string error;
  if (should_restart &&
      !cmd.UpdateAppInfo("all", request_.package_name(), &error)) {
    response->set_status(proto::SwapResponse::ACTIVITY_RESTART_FAILED);
  }

  if (swap_failed &&
      (response->status() == proto::SwapResponse::OK ||
       response->status() == proto::SwapResponse::ACTIVITY_RESTART_FAILED)) {
    // If we updated overlay even on swap fail or restart fail,
    // alter the response accordingly.
    response->set_status(proto::SwapResponse::SWAP_FAILED_BUT_OVERLAY_UPDATED);
  }
}

proto::SwapResponse::Status OverlaySwapCommand::OverlayStatusToSwapStatus(
    proto::OverlayUpdateResponse::Status status) {
  switch (status) {
    case proto::OverlayUpdateResponse::OK:
      return proto::SwapResponse::OK;
    case proto::OverlayUpdateResponse::ID_MISMATCH:
      return proto::SwapResponse::OVERLAY_ID_MISMATCH;
    default:
      return proto::SwapResponse::OVERLAY_UPDATE_FAILED;
  }
}

}  // namespace deploy
