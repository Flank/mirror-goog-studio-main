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
#include "tools/base/deploy/installer/server/app_servers.h"
#include "tools/base/deploy/sites/sites.h"

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

  proto::OverlayUpdateRequest update_request;
  update_request.set_expected_overlay_id(request_.prev_oid());
  update_request.set_overlay_id(request_.next_oid());
  const std::string pkg = request_.package_name();
  update_request.set_package_name(pkg);
  update_request.set_overlay_path(Sites::AppOverlays(pkg));
  update_request.set_wipe_all_files(request_.wipe_overlays());

  InstallClient* client_ =
      AppServers::Get(request_.package_name(), workspace_.GetTmpFolder(),
                      workspace_.GetVersion());

  auto resp = client_->UpdateOverlay(update_request);
  if (!resp) {
    ErrEvent("OverlayIdPushCommand comm error");
    return;
  }

  proto::OverlayUpdateResponse_Status status = resp->status();
  if (status != proto::OverlayUpdateResponse::OK) {
    ErrEvent("OverlayIdPushCommand error: Bad status (" + to_string(status) +
             ")");
  }

  dump_response->set_status(proto::OverlayIdPushResponse::OK);
}

}  // namespace deploy
