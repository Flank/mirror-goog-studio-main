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

  std::vector<int> pids(request_.process_ids().begin(),
                        request_.process_ids().end());
  SetSwapParameters(request_.package_name(), pids, request_.extra_agents());
  ready_to_run_ = true;
}

std::unique_ptr<proto::SwapRequest>
OverlaySwapCommand::PrepareAndBuildRequest() {
  Phase p("PreSwap");
  std::unique_ptr<proto::SwapRequest> request(new proto::SwapRequest());

  std::string version = workspace_.GetVersion() + "-";

  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  std::string agent =
      request_.arch() == proto::Arch::ARCH_64_BIT ? kAgent : kAgentAlt;
#else
  std::string agent = kAgent;
#endif

  std::string startup_path = Sites::AppStartupAgent(package_name_);
  std::string studio_path = Sites::AppStudio(package_name_);
  std::string agent_path = startup_path + version + agent;

  std::unordered_set<std::string> missing_files;
  if (!CheckFilesExist({startup_path, studio_path, agent_path},
                       &missing_files)) {
    return nullptr;
  }

  RunasExecutor run_as(package_name_);
  std::string error;

  bool missing_startup =
      missing_files.find(startup_path) != missing_files.end();
  bool missing_agent = missing_files.find(agent_path) != missing_files.end();

  // Clean up other agents from the startup_agent directory. Because agents are
  // versioned (agent-<version#>) we cannot simply copy our agent on top of the
  // previous file. If the startup_agent directory exists but our agent cannot
  // be found in it, we assume another agent is present and delete it.
  if (!missing_startup && missing_agent) {
    if (!run_as.Run("rm", {"-f", "-r", startup_path}, nullptr, &error)) {
      ErrEvent("Could not remove old agents: " + error);
      return nullptr;
    }
    missing_startup = true;
  }

  if (missing_startup &&
      !run_as.Run("mkdir", {startup_path}, nullptr, &error)) {
    ErrEvent("Could not create startup agent directory: " + error);
    return nullptr;
  }

  if (missing_files.find(studio_path) != missing_files.end() &&
      !run_as.Run("mkdir", {studio_path}, nullptr, &error)) {
    ErrEvent("Could not create .studio directory: " + error);
    return nullptr;
  }

  if (missing_agent &&
      !run_as.Run("cp", {"-F", workspace_.GetTmpFolder() + agent, agent_path},
                  nullptr, &error)) {
    ErrEvent("Could not copy binaries: " + error);
    return nullptr;
  }

  SetAgentPath(agent_path);

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
  GetAgentLogs(response);
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

void OverlaySwapCommand::GetAgentLogs(proto::SwapResponse* response) {
  Phase p("GetAgentLogs");
  proto::GetAgentExceptionLogRequest req;
  req.set_package_name(request_.package_name());

  // If this fails, we don't really care - it's a best-effort situation; don't
  // break the deployment because of it. Just log and move on.
  auto resp = client_->GetAgentExceptionLog(req);
  if (!resp) {
    return;
  }

  for (const auto& log : resp->logs()) {
    auto added = response->add_agent_logs();
    *added = log;
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
