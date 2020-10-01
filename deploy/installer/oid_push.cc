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

#include "tools/base/deploy/installer/oid_push.h"

#include <fcntl.h>

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/installer/server/install_server.h"

namespace deploy {

// 4 Arguments:
//   Package Name
//   Previous OID
//   Next OID
//   Clear Overlays (true/false)
void OverlayIdPushCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_overlay_id_push()) {
    return;
  }

  request_ = request.overlay_id_push();

  ready_to_run_ = true;
}

void OverlayIdPushCommand::Run(proto::InstallerResponse* response) {
  Phase p("Overlay ID Push");

  if (!ExtractBinaries(workspace_.GetTmpFolder(), {kInstallServer})) {
    ErrEvent("Extracting binaries failed");
    return;
  }

  auto dump_response = response->mutable_overlayidpush_response();

  proto::InstallServerRequest install_request;
  install_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  auto update_request = install_request.mutable_overlay_request();
  update_request->set_expected_overlay_id(request_.prev_oid());
  update_request->set_overlay_id(request_.next_oid());
  update_request->set_overlay_path("code_cache");
  update_request->set_wipe_all_files(request_.wipe_overlays());

  std::unique_ptr<InstallClient> client_ = StartInstallServer(
      Executor::Get(), workspace_.GetTmpFolder() + kInstallServer,
      request_.package_name(), kInstallServer + "-" + workspace_.GetVersion());

  if (!client_) {
    ErrEvent("OverlayIdPushCommand error: No client");
    return;
  }

  if (!client_->Write(install_request)) {
    ErrEvent(
        "OverlayIdPushCommand error: Unable to write request to AppServer");
    return;
  }

  // Wait for server overlay update response.
  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    return;
  }

  proto::OverlayUpdateResponse_Status status =
      install_response.overlay_response().status();
  if (status != proto::OverlayUpdateResponse::OK) {
    ErrEvent("OverlayIdPushCommand error: Bad status (" + to_string(status) +
             ")");
  }

  if (!client_->KillServerAndWait(&install_response)) {
    ErrEvent("OverlayIdPushCommand error: Unable to kill AppServer");
    return;
  }

  dump_response->set_status(proto::OverlayIdPushResponse::OK);
}

}  // namespace deploy
