/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "tools/base/deploy/installer/delta_install.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>

#include "delta_install.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/package_manager.h"
#include "tools/base/deploy/installer/patch_applier.h"

namespace {
#if defined(__ANDROID__)
#include <sys/system_properties.h>
int GetAPILevel() {
  char sdk_ver_str[PROP_VALUE_MAX] = "0";
  __system_property_get("ro.build.version.sdk", sdk_ver_str);
  return atoi(sdk_ver_str);
}
#else
int GetAPILevel() { return 21; }
#endif
}  // namespace

namespace deploy {

DeltaInstallCommand::DeltaInstallCommand(Workspace& workspace)
    : BaseInstallCommand(workspace) {}

void DeltaInstallCommand::ParseParameters(int argc, char** argv) {
  Metric m("DELTAINSTALL_UPLOAD");
  BaseInstallCommand::ParseParameters(argc, argv);
}

void DeltaInstallCommand::Run() {
  Metric m("DELTAINSTALL_INSTALL");

  auto response = new proto::DeltaInstallResponse();
  workspace_.GetResponse().set_allocated_deltainstall_response(response);

  int api_level = GetAPILevel();
  LogEvent("DeltaInstall found API level:" + to_string(api_level));
  if (api_level < 21) {
    Install();
  } else {
    StreamInstall();
  }
}

void DeltaInstallCommand::Install() {
  Phase p("DeltaInstallCommand::Install");
  if (install_info_.patchinstructions().size() != 1) {
    // TODO: ERROR GOES HERE
    return;
  }

  std::string tmp_apk_path =
      workspace_.GetTmpFolder() + to_string(GetTime()) + ".tmp.apk";
  // Create and open tmp apk
  int dst_fd = open(tmp_apk_path.c_str(), O_CREAT, O_WRONLY);

  // Write content of the tmp apk
  PatchApplier patchApplier(workspace_.GetRoot());
  bool patch_result =
      patchApplier.ApplyPatchToFD(install_info_.patchinstructions()[0], dst_fd);
  if (!patch_result) {
    close(dst_fd);
    unlink(tmp_apk_path.c_str());
    return;
  }
  close(dst_fd);

  // Feed tmp apk to Package Manager (and gather output)
  std::string output;
  PackageManager pm(workspace_);
  std::vector<std::string> options;
  for (const std::string& option : install_info_.options()) {
    options.emplace_back(option);
  }
  pm.Install(tmp_apk_path, options, &output);

  proto::DeltaInstallResponse* response =
      workspace_.GetResponse().mutable_deltainstall_response();
  response->set_install_output(output);

  // Clean up tmp apk
  unlink(tmp_apk_path.c_str());
}
void DeltaInstallCommand::StreamInstall() {
  Phase p("DeltaInstallCommand::StreamInstall");
  proto::DeltaInstallResponse* response =
      workspace_.GetResponse().mutable_deltainstall_response();
  // Create session
  CmdCommand cmd(workspace_);

  std::string session_id;
  std::string output;
  std::vector<std::string> options;
  for (const std::string& option : install_info_.options()) {
    options.emplace_back(option);
  }

  bool session_created = CreateInstallSession(&output, &options);
  if (session_created) {
    session_id = output;
  } else {
    ErrEvent("Unable to create session"_s + output);
    response->set_status(proto::DeltaInstallResponse_Status_ERROR);
    response->set_install_output(output);
    return;
  }

  LogEvent("DeltaInstall created session: '"_s + session_id + "'");

  if (!SendApksToPackageManager(session_id)) {
    response->set_status(proto::DeltaInstallResponse::ERROR);
    return;
  }

  // Commit session (and gather output)
  bool commit_result = cmd.CommitInstall(session_id, &output);
  response->set_install_output(output);
  if (!commit_result) {
    ErrEvent(output);
  }
  // Since old versions of Android do not return a proper status code,
  // commit_result cannot be reliable used to determine if the installation
  // succedded.
  response->set_status(proto::DeltaInstallResponse_Status_OK);
}
}  // namespace deploy
