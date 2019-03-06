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

#include "delta_install.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>

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
    : Command(workspace) {}

void DeltaInstallCommand::ParseParameters(int argc, char** argv) {
  Phase p("DeltaInstallCommand::ParseParameters");
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;

  BeginPhase("Reading stdin");
  if (!wrapper.Read(&data)) {
    ErrEvent("Unable to read data on stdin.");
    EndPhase();
    return;
  }
  EndPhase();

  BeginPhase("Parsing input ");
  if (!request_.ParseFromString(data)) {
    ErrEvent("Unable to parse protobuffer request object.");
    EndPhase();
    return;
  }
  EndPhase();

  ready_to_run_ = true;
}

void DeltaInstallCommand::Run() {
  Phase p("Command DeltaInstall");

  proto::DeltaInstallResponse* response = new proto::DeltaInstallResponse();
  workspace_.GetResponse().set_allocated_deltainstall_response(response);

  int api_level = GetAPILevel();
  LogEvent("DeltaInstall found API level:" + to_string(api_level));
  if (api_level < 21) {
    Install();
  } else {
    StreamInstall();
  }
}

bool DeltaInstallCommand::SendApkToPackageManager(
    const proto::PatchInstruction& patch, const std::string& session_id) {
  Phase p("DeltaInstallCommand::SendApkToPackageManager");

  // Special case where there is no patch, the apk has not changed, we can skip
  // it altogether since the session was created with inheritance (-p).
  if (patch.patches().size() == 0) {
    return true;
  }

  // Open a stream to the package manager to write to.
  std::string output;
  std::string error;
  std::vector<std::string> parameters;
  parameters.push_back("package");
  parameters.push_back("install-write");
  parameters.push_back("-S");
  parameters.push_back(to_string(patch.dst_filesize()));
  parameters.push_back(session_id);
  std::string apk = patch.src_absolute_path();
  parameters.push_back(apk.substr(apk.rfind("/") + 1));

  for (std::string& parameter : parameters) {
    LogEvent(parameter);
  }

  int pm_stdout, pm_stderr, pm_stdin, pid;
  workspace_.GetExecutor().ForkAndExec("cmd", parameters, &pm_stdin, &pm_stdout,
                                       &pm_stderr, &pid);

  PatchApplier patchApplier;
  patchApplier.ApplyPatchToFD(patch, pm_stdin);

  // Clean up
  close(pm_stdin);
  close(pm_stdout);
  close(pm_stderr);
  int status;
  waitpid(pid, &status, 0);

  bool successs = WIFEXITED(status) && (WEXITSTATUS(status) == 0);
  if (!successs) {
    ErrEvent("Error while sending APKs to PM");
    ErrEvent(output);
    return false;
  }
  return true;
}

void DeltaInstallCommand::Install() {
  Phase p("DeltaInstallCommand::Install");
  if (request_.patchinstructions().size() != 1) {
    // TODO: ERROR GOES HERE
    return;
  }

  std::string tmp_apk_path =
      workspace_.GetTmpFolder() + to_string(GetTime()) + ".tmp.apk";
  // Create and open tmp apk
  int dst_fd = open(tmp_apk_path.c_str(), O_CREAT, O_WRONLY);

  // Write content of the tmp apk
  PatchApplier patchApplier;
  bool patch_result =
      patchApplier.ApplyPatchToFD(request_.patchinstructions()[0], dst_fd);
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
  for (const std::string& option : request_.options()) {
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
  for (const std::string& option : request_.options()) {
    options.emplace_back(option);
  }

  // Use inheritance so we can skip unchanged APKs in cases where
  // the application uses splits.
  options.emplace_back("-p");
  options.emplace_back(request_.package_name());

  if (!cmd.CreateInstallSession(&output, options)) {
    ErrEvent("Unable to create session"_s + output);
    response->set_status(proto::DeltaInstallResponse_Status_ERROR);
    response->set_install_output(output);
    return;
  } else {
    session_id = output;
  }
  LogEvent("DeltaInstall created session: '"_s + session_id + "'");

  // For all apks involved, stream the patched content to the Package Manager
  for (const proto::PatchInstruction& patch : request_.patchinstructions()) {
    bool send_result = SendApkToPackageManager(patch, session_id);
    if (!send_result) {
      std::string abort_output;
      cmd.AbortInstall(session_id, &abort_output);
      return;
    }
  }

  // Commit session (and gather output)
  bool commit_result = cmd.CommitInstall(session_id, &output);
  response->set_install_output(output);
  if (!commit_result) {
    ErrEvent(output);
    response->set_status(proto::DeltaInstallResponse_Status_ERROR);
    return;
  }

  response->set_status(proto::DeltaInstallResponse_Status_OK);
}

}  // namespace deploy
