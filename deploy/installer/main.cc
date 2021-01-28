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

#include <algorithm>
#include <iostream>
#include <map>
#include <sstream>
#include <string>

#include <getopt.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#ifdef __APPLE__
#include <mach-o/dyld.h>
#endif

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/dump.h"
#include "tools/base/deploy/installer/executor/executor_impl.h"
#include "tools/base/deploy/installer/executor/redirect_executor.h"
#include "tools/base/deploy/installer/highlander.h"
#include "tools/base/deploy/installer/server/app_servers.h"
#include "tools/base/deploy/installer/workspace.h"
#include "tools/base/deploy/proto/deploy.pb.h"

using namespace deploy;

static const char* kNoValue = "";

struct Parameters {
  const char* cmd_path = kNoValue;
  const char* pm_path = kNoValue;
  const char* version = kNoValue;
};

// In daemon mode, the installer servers requests continuously from stdin.
static bool daemon_mode = false;
static bool running = true;

void ProcessRequest(std::unique_ptr<proto::InstallerRequest>, Workspace&);

Parameters ParseArgs(int argc, char** argv) {
  Parameters parameters;

  int index = 1;
  while (index < argc && argv[index][0] == '-') {
    strtok(argv[index], "=");
    if (!strncmp("-cmd", argv[index], 4)) {
      parameters.cmd_path = strtok(nullptr, "=");
    } else if (!strncmp("-pm", argv[index], 3)) {
      parameters.pm_path = strtok(nullptr, "=");
    } else if (!strncmp("-daemon", argv[index], 7)) {
      daemon_mode = true;
    } else if (!strncmp("-version", argv[index], 8)) {
      parameters.version = strtok(nullptr, "=");
    }
    index++;
  }

  return parameters;
}

void SendResponse(proto::InstallerResponse* response,
                  const Workspace& workspace) noexcept {
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response->add_events());
  }
  std::string response_string;
  response->SerializeToString(&response_string);
  workspace.GetOutput().Write(response_string);
}

void Fail(proto::InstallerResponse::Status status, Workspace& workspace,
         const std::string& message) {
  proto::InstallerResponse response;
  response.set_status(status);
  ErrEvent(message);
  SendResponse(&response, workspace);
}

std::string GetVersion() {
  static std::string version = "";

  if (!version.empty()) {
    return version;
  }

  matryoshka::Doll* doll = matryoshka::OpenByName("version");
  if (doll) {
    version = std::string((char*)doll->content, doll->content_len);
    delete doll;
    return version;
  } else {
    return "UNVERSIONED";
  }
}

std::unique_ptr<proto::InstallerRequest> GetRequestFromFD(int input_fd) {
  deploy::MessagePipeWrapper wrapper(input_fd);
  std::string data;
  if (!wrapper.Read(&data)) {
    return nullptr;
  }
  proto::InstallerRequest* request = new proto::InstallerRequest();
  if (!request->ParseFromString(data)){
    return nullptr;
  }

  std::unique_ptr<proto::InstallerRequest> ptr(request);
  return ptr;
}

void CheckVersion(const std::string& version, Workspace& workspace) {
  // Verify that this program is the version the caller expected.
  if (version == GetVersion()) {
    return;
  }

  // Wrong version!
  std::string message =
      "Version mismatch. Requested:"_s + version + " but have " + GetVersion();
  Fail(proto::InstallerResponse::ERROR_WRONG_VERSION, workspace, message);
  exit(EXIT_SUCCESS);
}

// Parse commandline parameters and impact workspace accordindly.
void Init(int argc, char** argv, Workspace* workspace) {
  // Check and parse parameters
  Parameters parameters = ParseArgs(argc, argv);

  if (parameters.cmd_path != kNoValue) {
    workspace->SetCmdPath(parameters.cmd_path);
  }
  if (parameters.pm_path != kNoValue) {
    workspace->SetPmPath(parameters.pm_path);
  }

  workspace->Init();

  if (parameters.version != kNoValue) {
    CheckVersion(parameters.version, *workspace);
  }
}

void ProcessRequest(std::unique_ptr<proto::InstallerRequest> request,
                    Workspace& workspace) {
  ResetEvents();
  Phase p("Installer request:" + request->command_name());

  if (!daemon_mode) {
    running = false;
  }

  CheckVersion(request->version(), workspace);

  // Retrieve Command to be invoked.
  auto task = GetCommand(request->command_name().c_str(), workspace);
  if (task == nullptr) {
    std::string msg =
        "Command name '"_s + request->command_name() + "' is unknown";
    Fail(proto::InstallerResponse::ERROR_CMD, workspace, msg);
    return;
  }

  // Check parameters
  task->ParseParameters(*request);
  if (!task->ReadyToRun()) {
    std::string msg =
        "Command '"_s + request->command_name() + "': bad parameters";
    Fail(proto::InstallerResponse::ERROR_PARAMETER, workspace, msg);
    return;
  }

  // Finally! Run !
  proto::InstallerResponse response;
  task->Run(&response);
  response.set_status(proto::InstallerResponse::OK);
  EndPhase();
  SendResponse(&response, workspace);
}

int main(int argc, char** argv) {
  InitEventSystem();

  Workspace workspace(GetVersion());

  Init(argc, argv, &workspace);

  // There should be only one...instance of installer running at
  // all time on a device. Kill(2) other instances if necessary.
  Highlander highlander(workspace);

  // Since we keep pipes open towards appserverd process, we don't want
  // to get a SIGPIPE signal writing to a closed pipe (dead appserverd).
  // We request to get EPIPE instead of a signal.
  signal(SIGPIPE, SIG_IGN);

  while (running) {
    // Retrieve request from stdin.
    auto request = GetRequestFromFD(STDIN_FILENO);
    if (request == nullptr) {
      break;
    }
    ProcessRequest(std::move(request), workspace);
  }

  AppServers::Clear();
  return EXIT_SUCCESS;
}
