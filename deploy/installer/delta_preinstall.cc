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

#include "tools/base/deploy/installer/delta_preinstall.h"

#include <assert.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <algorithm>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/patch_applier.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

void DeltaPreinstallCommand::Run(proto::InstallerResponse* response) {
  Metric m("DELTAPREINSTALL_WRITE");

  auto delta_response = response->mutable_deltapreinstall_response();

  // Create a session
  CmdCommand cmd(workspace_);
  std::string output;
  std::string session_id;

  std::vector<std::string> options;
  for (const std::string& option : install_info_.options()) {
    options.emplace_back(option);
  }
  options.emplace_back("-t");
  options.emplace_back("-r");
  options.emplace_back("--dont-kill");

  bool session_created = CreateInstallSession(&output, &options);
  if (session_created) {
    session_id = output;
    delta_response->set_session_id(session_id);
  } else {
    ErrEvent("Unable to create session");
    ErrEvent(output);
    delta_response->set_status(proto::DeltaStatus::ERROR);
    return;
  }

  if (!SendApksToPackageManager(session_id)) {
    delta_response->set_status(proto::DeltaStatus::STREAM_APK_FAILED);
    return;
  }

  delta_response->set_status(proto::DeltaStatus::OK);
}

}  // namespace deploy
