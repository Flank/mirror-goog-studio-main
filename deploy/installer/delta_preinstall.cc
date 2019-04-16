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
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/installer/patch_applier.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

void DeltaPreinstallCommand::ParseParameters(int argc, char** argv) {
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;

  BeginMetric("DELTAPREINSTALL::PUSH");
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

bool DeltaPreinstallCommand::SendApkToPackageManager(
    const proto::PatchInstruction& patch, const std::string& session_id) {
  Phase p("Write to PM");

  // Special case where there is no patch, the apk has not changed, we can skip
  // it altogether since the session was created with inheritance (-p).
  if (patch.patches().size() == 0) {
    LogEvent("Skipping!");
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

  int pm_stdout, pm_stderr, pm_stdin, pid;
  workspace_.GetExecutor().ForkAndExec("cmd", parameters, &pm_stdin, &pm_stdout,
                                       &pm_stderr, &pid);

  PatchApplier patchApplier(workspace_.GetRoot());
  patchApplier.ApplyPatchToFD(patch, pm_stdin);

  // Clean up
  close(pm_stdin);
  close(pm_stdout);
  close(pm_stderr);
  int status;
  waitpid(pid, &status, 0);

  return WIFEXITED(status) && (WEXITSTATUS(status) == 0);
}

void DeltaPreinstallCommand::Run() {
  Metric m("DELTAPREINSTALL::WRITE");

  proto::DeltaPreinstallResponse* response =
      new proto::DeltaPreinstallResponse();
  workspace_.GetResponse().set_allocated_deltapreinstall_response(response);

  // Create a session
  CmdCommand cmd(workspace_);
  std::string output;
  std::string session_id;

  std::vector<std::string> options;
  options.emplace_back("-t");
  options.emplace_back("-r");
  options.emplace_back("--dont-kill");

  // Use inheritance so we can skip unchanged APKs in cases where
  // the application uses splits.
  options.emplace_back("-p");
  options.emplace_back(request_.package_name());

  if (!cmd.CreateInstallSession(&output, options)) {
    ErrEvent(output);
    response->set_status(proto::DeltaPreinstallResponse::ERROR);
    return;
  } else {
    session_id = output;
    response->set_session_id(session_id);
  }

  for (const proto::PatchInstruction& patch : request_.patchinstructions()) {
    SendApkToPackageManager(patch, session_id);
  }

  response->set_status(proto::DeltaPreinstallResponse::OK);
}

}  // namespace deploy
