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
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/patch_applier.h"

namespace deploy {

DeltaInstallCommand::DeltaInstallCommand(Workspace& workspace)
    : BaseInstallCommand(workspace) {}

void DeltaInstallCommand::ParseParameters(const proto::InstallerRequest& request) {
  Metric m("DELTAINSTALL_UPLOAD");
  BaseInstallCommand::ParseParameters(request);
}

void DeltaInstallCommand::Run(proto::InstallerResponse* response) {
  Metric m("DELTAINSTALL_INSTALL");

  int api_level = Env::api_level();
  LogEvent("DeltaInstall found API level:" + to_string(api_level));

  proto::DeltaInstallResponse* delta_response =
      response->mutable_deltainstall_response();
  if (api_level < 21) {
    delta_response->set_status(proto::DeltaStatus::STREAM_APK_NOT_SUPPORTED);
    return;
  }

  StreamInstall(delta_response);
}

void DeltaInstallCommand::StreamInstall(proto::DeltaInstallResponse* response) {
  Phase p("DeltaInstallCommand::StreamInstall");
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
    response->set_status(proto::DeltaStatus::ERROR);
    response->set_install_output(output);
    return;
  }

  LogEvent("DeltaInstall created session: '"_s + session_id + "'");

  if (!SendApksToPackageManager(session_id)) {
    response->set_status(proto::DeltaStatus::STREAM_APK_FAILED);
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
  response->set_status(proto::DeltaStatus::OK);
}
}  // namespace deploy
