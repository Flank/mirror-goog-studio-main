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
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/installer/server/install_server.h"

namespace deploy {

const int kRwFileMode =
    S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRxFileMode =
    S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH;

// 4 Arguments:
//   Package Name
//   Previous OID
//   Next OID
//   Clear Overlays (true/false)
void OverlayIdPushCommand::ParseParameters(int argc, char** argv) {
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;
  if (!wrapper.Read(&data)) {
    return;
  }

  if (!request_.ParseFromString(data)) {
    return;
  }

  ready_to_run_ = true;
}

void OverlayIdPushCommand::Run(proto::InstallerResponse* response) {
  Phase p("Overlay ID Push");

  if (!ExtractBinaries(workspace_.GetTmpFolder(), {kInstallServer})) {
    // TODO Error Handling
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
      workspace_.GetExecutor(), workspace_.GetTmpFolder() + kInstallServer,
      request_.package_name(), kInstallServer + "-" + workspace_.GetVersion());

  if (!client_) {
    // TODO Error Handling
    return;
  }

  if (!client_->Write(install_request)) {
    // TODO Error Handling.
    return;
  }

  // Wait for server overlay update response.
  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    return;
  }

  if (install_response.overlay_response().status() !=
      proto::OverlayUpdateResponse::OK) {
    // TODO Error Handling.
  }

  if (!client_->KillServerAndWait(&install_response)) {
    // TODO Error Handling.
    return;
  }

  dump_response->set_status(proto::OverlayIdPushResponse::OK);
}

}  // namespace deploy
