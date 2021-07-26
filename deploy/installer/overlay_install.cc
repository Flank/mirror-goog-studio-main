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

#include "tools/base/deploy/installer/overlay_install.h"

#include <string>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/server/app_servers.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

void OverlayInstallCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_overlay_install()) {
    return;
  }
  request_ = request.overlay_install();
  package_name_ = request_.package_name();

  ready_to_run_ = true;
}

void OverlayInstallCommand::Run(proto::InstallerResponse* response) {
  if (!PrepareInteraction(request_.arch())) {
    ErrEvent("Unable to prepare interaction");
    return;
  }

  proto::OverlayInstallResponse* overlay_response =
      response->mutable_overlay_install_response();

  UpdateOverlay(overlay_response);
  auto logsResp = GetAgentLogs();
  if (logsResp == nullptr) {
    return;
  }

  for (const auto& log : logsResp->logs()) {
    auto added = overlay_response->add_agent_logs();
    *added = log;
  }
}

void OverlayInstallCommand::UpdateOverlay(
    proto::OverlayInstallResponse* overlay_response) {
  Phase p("UpdateOverlay");

  proto::OverlayUpdateRequest overlay_request;
  overlay_request.set_overlay_id(request_.overlay_id());
  overlay_request.set_expected_overlay_id(request_.expected_overlay_id());

  const std::string overlay_path = Sites::AppOverlays(request_.package_name());
  overlay_request.set_overlay_path(overlay_path);
  overlay_request.set_package_name(request_.package_name());

  for (auto overlay_file : request_.overlay_files()) {
    auto file = overlay_request.add_files_to_write();
    file->set_path(overlay_file.path());
    file->set_allocated_content(overlay_file.release_content());
  }

  for (auto deleted_file : request_.deleted_files()) {
    overlay_request.add_files_to_delete(deleted_file);
  }

  auto resp = client_->UpdateOverlay(overlay_request);
  if (!resp) {
    ErrEvent("Could send update to install server");
    overlay_response->set_status(
        proto::OverlayInstallResponse::INSTALL_SERVER_COM_ERR);
    return;
  }

  switch (resp->status()) {
    case proto::OverlayUpdateResponse::OK:
      overlay_response->set_status(proto::OverlayInstallResponse::OK);
      return;
    case proto::OverlayUpdateResponse::ID_MISMATCH:
      overlay_response->set_status(
          proto::OverlayInstallResponse::OVERLAY_ID_MISMATCH);
      overlay_response->set_extra(resp->error_message());
      return;
    case proto::OverlayUpdateResponse::UPDATE_FAILED:
      overlay_response->set_status(
          proto::OverlayInstallResponse::OVERLAY_UPDATE_FAILED);
      overlay_response->set_extra(resp->error_message());
      return;
  }
}
}  // namespace deploy
