/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "tools/base/deploy/installer/base_install.h"

#include <sys/wait.h>

#include "base_install.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/patch_applier.h"

namespace deploy {

deploy::BaseInstallCommand::BaseInstallCommand(Workspace& workspace)
    : Command(workspace) {}

void BaseInstallCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_install_info_request()) {
    ErrEvent("Unable to get install info.");
    return;
  }

  install_info_ = request.install_info_request();

  ready_to_run_ = true;
}

bool BaseInstallCommand::SendApkToPackageManager(
    const proto::PatchInstruction& patch, const std::string& session_id) {
  Phase p("DeltaInstallCommand::SendApkToPackageManager");

  // Open a stream to the package manager to write to.
  std::string output;
  std::string error;
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-write");
  parameters.emplace_back("-S");
  parameters.emplace_back(to_string(patch.dst_filesize()));
  parameters.emplace_back(session_id);
  std::string apk = patch.src_absolute_path();
  parameters.emplace_back(apk.substr(apk.rfind('/') + 1));

  for (std::string& parameter : parameters) {
    LogEvent(parameter);
  }

  int pm_stdin, pid;
  Executor::Get().ForkAndExec("cmd", parameters, &pm_stdin, nullptr, nullptr,
                              &pid);

  PatchApplier patchApplier;
  bool patch_result = patchApplier.ApplyPatchToFD(patch, pm_stdin);

  // Clean up
  close(pm_stdin);
  int status;
  waitpid(pid, &status, 0);

  // Patch failed ?
  if (!patch_result) {
    ErrEvent("Patching '"_s + patch.src_absolute_path() + "' failed");
    return false;
  }

  // PM failed ?
  bool success = WIFEXITED(status) && (WEXITSTATUS(status) == 0);
  if (!success) {
    ErrEvent("Error while sending APKs to PM");
    ErrEvent(output);
    return false;
  }
  return true;
}

bool BaseInstallCommand::CreateInstallSession(
    std::string* output, std::vector<std::string>* options) {
  // Use inheritance so we can skip unchanged APKs in cases where
  // the application uses splits.
  if (install_info_.inherit()) {
    options->emplace_back("-p");
    options->emplace_back(install_info_.package_name());
  }

  CmdCommand cmd(workspace_);
  return cmd.CreateInstallSession(output, *options);
}

bool BaseInstallCommand::SendApksToPackageManager(
    const std::string& session_id) {
  CmdCommand cmd(workspace_);

  // For all apks involved, stream the patched content to the Package Manager
  for (const proto::PatchInstruction& patch :
       install_info_.patchinstructions()) {
    // Skip if we are inheriting and this apk did not change
    if (install_info_.inherit() && patch.patches().size() == 0) {
      LogEvent("Skipping '"_s + patch.src_absolute_path() +
               "' since inheriting mode and apk did not change");
      continue;
    }
    bool send_result = SendApkToPackageManager(patch, session_id);
    if (!send_result) {
      std::string abort_output;
      cmd.AbortInstall(session_id, &abort_output);
      ErrEvent("Unable to stream '"_s + patch.src_absolute_path() + "' to PM");
      return false;
    }
    LogEvent("Streaming succeeded for '"_s + patch.src_absolute_path() + "'");
  }
  return true;
}

}  // namespace deploy