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
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/installer/server/install_server.h"

namespace deploy {

const int kRwFileMode =
    S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRxFileMode =
    S_IRUSR | S_IXUSR | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH;

// Only one argument, the OID is expected.
void OverlayIdPushCommand::ParseParameters(int argc, char** argv) {
  if (argc < 2) {
    return;
  }

  package_name_ = argv[0];
  oid_ = argv[1];

  ready_to_run_ = true;
}

bool OverlayIdPushCommand::ExtractBinaries(
    const std::string& target_dir,
    const std::vector<std::string>& files_to_extract) const {
  Phase p("ExtractBinaries");

  std::vector<std::unique_ptr<matryoshka::Doll>> dolls;
  for (const std::string& file : files_to_extract) {
    const std::string tmp_path = target_dir + file;

    // If we've already extracted the file, we don't need to re-extract.
    if (access(tmp_path.c_str(), F_OK) == 0) {
      continue;
    }

    // Open the matryoshka if we haven't already done so.
    if (dolls.empty() && !matryoshka::Open(dolls)) {
      ErrEvent("Installer binary does not contain any other binaries.");
      return false;
    }

    // Find the binary that corresponds to this file and write it to disk.
    matryoshka::Doll* doll = matryoshka::FindByName(dolls, file);
    if (!doll) {
      continue;
    }

    if (!WriteArrayToDisk(doll->content, doll->content_len,
                          target_dir + file)) {
      ErrEvent("Failed writing to disk");
      return false;
    }
  }

  return true;
}

bool OverlayIdPushCommand::WriteArrayToDisk(const unsigned char* array,
                                            uint64_t array_len,
                                            const std::string& dst_path) const
    noexcept {
  Phase p("WriteArrayToDisk");
  std::string real_path = workspace_.GetRoot() + dst_path;
  int fd = open(real_path.c_str(), O_WRONLY | O_CREAT, kRwFileMode);
  if (fd == -1) {
    ErrEvent("WriteArrayToDisk, open: "_s + strerror(errno));
    return false;
  }
  int written = write(fd, array, array_len);
  if (written == -1) {
    ErrEvent("WriteArrayToDisk, write: "_s + strerror(errno));
    return false;
  }

  int close_result = close(fd);
  if (close_result == -1) {
    ErrEvent("WriteArrayToDisk, close: "_s + strerror(errno));
    return false;
  }

  chmod(real_path.c_str(), kRxFileMode);
  return true;
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
  update_request->set_overlay_id(oid_);
  update_request->set_expected_overlay_id("");
  update_request->set_overlay_path("code_cache");

  std::unique_ptr<InstallClient> client_ = StartInstallServer(
      workspace_.GetExecutor(), workspace_.GetTmpFolder() + kInstallServer,
      package_name_, kInstallServer + "-" + workspace_.GetVersion());

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
