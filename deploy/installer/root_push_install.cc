/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "tools/base/deploy/installer/root_push_install.h"

#include <ftw.h>

#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/patch_applier.h"

namespace deploy {
namespace {
const char* kTempSuffix = ".tmp";
}
void RootPushInstallCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_root_push_install_request()) {
    ErrEvent("rootpushinstall: unable to get rootpushinstall request.");
    return;
  }

  request_ = request.root_push_install_request();
  ready_to_run_ = true;
}

void RootPushInstallCommand::Run(proto::InstallerResponse* response) {
  Phase p("Command RootPushInstall");

  auto install_response = response->mutable_root_push_install_response();

  // Delete the native libs dir. We need to force the framework to read them
  // directly from the APKs. This directory is only recreated when the app is
  // installed via the package manager, so this deletion will not happen
  // frequently.
  auto lib_dir = request_.install_dir() + "/lib";
  if (access(lib_dir.c_str(), F_OK) == 0 &&
      nftw(lib_dir.c_str(),
           [](const char* path, const struct stat* sbuf, int type,
              struct FTW* ftwb) { return remove(path); },
           10 /*max FD*/, FTW_DEPTH | FTW_MOUNT | FTW_PHYS) != 0) {
    install_response->set_status(proto::RootPushInstallResponse::ERROR);
    install_response->set_error_message(
        "rootpushinstall: deleting lib dir failed: "_s + strerror(errno));
    return;
  }

  auto install_info = request_.install_info();
  for (const proto::PatchInstruction& patch :
       install_info.patchinstructions()) {
    // Skip if this apk did not change
    if (patch.patches().size() == 0) {
      LogEvent("rootpushinstall: skipping '"_s + patch.src_absolute_path() +
               "' since apk did not change");
      continue;
    }

    // If any of the following operations fail, the expectation is that the host
    // will perform a correct install to properly complete the installation,
    // which will naturally clean up any left behind temp files.

    PatchApplier patchApplier;
    std::string tmp_file = patch.src_absolute_path() + kTempSuffix;
    int fd = IO::creat(tmp_file, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
    if (fd < 0) {
      install_response->set_status(proto::RootPushInstallResponse::ERROR);
      install_response->set_error_message(
          "rootpushinstall: creat() failed: "_s + strerror(errno));
      return;
    }

    bool patch_result = patchApplier.ApplyPatchToFD(patch, fd);
    if (!patch_result) {
      install_response->set_status(proto::RootPushInstallResponse::ERROR);
      install_response->set_error_message(
          "rootpushinstall: unable to patch '"_s + patch.src_absolute_path() +
          "'");
      return;
    }

    if (close(fd) < 0) {
      install_response->set_status(proto::RootPushInstallResponse::ERROR);
      install_response->set_error_message(
          "rootpushinstall:n close() failed: "_s + strerror(errno));
      return;
    }

    if (IO::rename(tmp_file, patch.src_absolute_path()) < 0) {
      install_response->set_status(proto::RootPushInstallResponse::ERROR);
      install_response->set_error_message(
          "rootpushinstall: rename() failed: "_s + strerror(errno));
      return;
    }

    LogEvent("rootpushinstall: patching succeeded for '"_s +
             patch.src_absolute_path() + "'");
    install_response->set_status(proto::RootPushInstallResponse_Status_OK);
  }
}

}  // namespace deploy
