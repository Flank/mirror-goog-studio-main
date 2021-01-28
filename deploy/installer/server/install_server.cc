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

#include "tools/base/deploy/installer/server/install_server.h"

#include <dirent.h>
#include <fcntl.h>
#include <ftw.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/wait.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/overlay/overlay.h"
#include "tools/base/deploy/sites/sites.h"

using ServerRequest = proto::InstallServerRequest;
using ServerResponse = proto::InstallServerResponse;

namespace deploy {

InstallServer::~InstallServer() { Close(); }

void InstallServer::Close() {
  input_.Close();
  output_.Close();
}

void InstallServer::Run() {
  ServerRequest request;
  while (input_.Read(-1, &request)) {
    // Check canary before doing anything else.
    if (!canary_.Tweet()) {
      // The canary has died. Likely the app was uninstalled.
      // The uid does not have access to /data/data/<PKG_NAME> anymore.
      // The only option is to stop operating.
      Log::E("Stopping appserverd since canary has died\"");
      break;
    }

    HandleRequest(request);
  }
  Close();
}

void InstallServer::HandleRequest(const ServerRequest& request) {
  ResetEvents();
  ServerResponse response;
  response.set_status(ServerResponse::REQUEST_COMPLETED);

  switch (request.message_case()) {
    case ServerRequest::MessageCase::kSocketRequest:
      HandleOpenSocket(request.socket_request(),
                       response.mutable_socket_response());
      break;
    case ServerRequest::MessageCase::kSendRequest:
      HandleSendMessage(request.send_request(),
                        response.mutable_send_response());
      break;
    case ServerRequest::MessageCase::kCheckRequest:
      HandleCheckSetup(request.check_request(),
                       response.mutable_check_response());

      break;
    case ServerRequest::MessageCase::kOverlayRequest:
      HandleOverlayUpdate(request.overlay_request(),
                          response.mutable_overlay_response());
      break;
    case ServerRequest::MessageCase::kLogRequest:
      HandleGetAgentExceptionLog(request.log_request(),
                                 response.mutable_log_response());
      break;
    case ServerRequest::MessageCase::MESSAGE_NOT_SET:
      ErrEvent("Cannot process InstallServer request without message");
      response.set_status(ServerResponse::ERROR);
      break;
    default:
      response.set_status(ServerResponse::ERROR);
      break;
  }

  // Consume traces and proto events.
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response.add_events());
  }

  output_.Write(response);
}

void InstallServer::HandleOpenSocket(
    const proto::OpenAgentSocketRequest& request,
    proto::OpenAgentSocketResponse* response) {
  agent_server_.Close();
  if (agent_server_.Open() &&
      agent_server_.BindAndListen(request.socket_name())) {
    response->set_status(proto::OpenAgentSocketResponse::OK);
  } else {
    ErrEvent("Unable to bind socket '" + request.socket_name() + "'");
    response->set_status(proto::OpenAgentSocketResponse::BIND_SOCKET_FAILED);
  }
}

void InstallServer::HandleSendMessage(
    const proto::SendAgentMessageRequest& request,
    proto::SendAgentMessageResponse* response) {
  HandleSendMessageInner(request, response);
  agent_server_.Close();
}

void InstallServer::HandleSendMessageInner(
    const proto::SendAgentMessageRequest& request,
    proto::SendAgentMessageResponse* response) {
  const std::string request_bytes = request.agent_request().SerializeAsString();
  std::vector<std::unique_ptr<Socket>> agents;
  for (int i = 0; i < request.agent_count(); ++i) {
    std::unique_ptr<Socket> agent;
    // 15 seconds since there is a chance we need to wait for the
    // host to attach an agent from the debugger.
    if ((agent = agent_server_.Accept(15000)) == nullptr) {
      response->set_status(
          proto::SendAgentMessageResponse::AGENT_ACCEPT_FAILED);
      return;
    }

    if (!agent->Write(request_bytes)) {
      response->set_status(
          proto::SendAgentMessageResponse::WRITE_TO_AGENT_FAILED);
      return;
    }

    agents.push_back(std::move(agent));
  }

  for (auto& agent : agents) {
    std::string message;
    if (!agent->Read(&message)) {
      response->set_status(
          proto::SendAgentMessageResponse::READ_FROM_AGENT_FAILED);
      return;
    }

    if (!response->add_agent_responses()->ParseFromString(message)) {
      response->set_status(
          proto::SendAgentMessageResponse::UNPARSEABLE_AGENT_RESPONSE);
      return;
    }
  }

  response->set_status(proto::SendAgentMessageResponse::OK);
}

void InstallServer::HandleCheckSetup(
    const proto::CheckSetupRequest& request,
    proto::CheckSetupResponse* response) const {
  for (const std::string& file : request.files()) {
    if (IO::access(file, F_OK) != 0) {
      response->add_missing_files(file);
    }
  }
}

void InstallServer::HandleOverlayUpdate(
    const proto::OverlayUpdateRequest& request,
    proto::OverlayUpdateResponse* response) const {
  const std::string overlay_folder = request.overlay_path();
  if (request.wipe_all_files()) {
    if (nftw(overlay_folder.c_str(),
             [](const char* path, const struct stat* sbuf, int type,
                struct FTW* ftwb) { return remove(path); },
             10 /*max FD*/, FTW_DEPTH | FTW_MOUNT | FTW_PHYS) != 0) {
      response->set_status(proto::OverlayUpdateResponse::UPDATE_FAILED);
      response->set_error_message("Could not wipe existing overlays");
    }
  }

  if (!DoesOverlayIdMatch(overlay_folder, request.expected_overlay_id())) {
    response->set_status(proto::OverlayUpdateResponse::ID_MISMATCH);
    return;
  }

  Overlay overlay(overlay_folder, request.overlay_id());
  if (!overlay.Open()) {
    response->set_status(proto::OverlayUpdateResponse::UPDATE_FAILED);
    response->set_error_message("Could not open overlay");
    return;
  }

  // Live Literal instrumentation, while persistent across restarts, are not
  // considered part of the APK's install. We want all installs to nuke
  // all live literals information and the source of truth of all literal
  // updates will be based on this last install.
  overlay.DeleteDirectory(Sites::AppLiveLiteral(request.package_name()));

  for (const std::string& file : request.files_to_delete()) {
    if (!overlay.DeleteFile(file)) {
      response->set_status(proto::OverlayUpdateResponse::UPDATE_FAILED);
      response->set_error_message("Could not delete file: '" + file + "'");
      return;
    }
  }

  for (const proto::OverlayFile& file : request.files_to_write()) {
    if (!overlay.WriteFile(file.path(), file.content())) {
      response->set_status(proto::OverlayUpdateResponse::UPDATE_FAILED);
      response->set_error_message("Could not write file: '" + file.path() +
                                  "'");
      return;
    }
  }

  if (!overlay.Commit()) {
    response->set_status(proto::OverlayUpdateResponse::UPDATE_FAILED);
    response->set_error_message("Could not commit overlay update");
    return;
  }

  response->set_status(proto::OverlayUpdateResponse::OK);
}

void InstallServer::HandleGetAgentExceptionLog(
    const proto::GetAgentExceptionLogRequest& request,
    proto::GetAgentExceptionLogResponse* response) const {
  const std::string log_dir = Sites::AppLog(request.package_name());
  DIR* dir = IO::opendir(log_dir);
  if (dir == nullptr) {
    return;
  }
  dirent* entry;
  while ((entry = readdir(dir)) != nullptr) {
    if (entry->d_type != DT_REG) {
      continue;
    }
    const std::string log_path = log_dir + entry->d_name;
    struct stat info;
    if (IO::stat(log_path, &info) != 0) {
      continue;
    }

    int fd = IO::open(log_path, O_RDONLY);
    if (fd < 0) {
      continue;
    }

    std::vector<char> bytes;
    bytes.reserve(info.st_size);
    if (read(fd, bytes.data(), info.st_size) == info.st_size) {
      response->add_logs()->ParseFromArray(bytes.data(), info.st_size);
    }
    IO::unlink(log_path);
  }
  closedir(dir);
}

bool InstallServer::DoesOverlayIdMatch(const std::string& overlay_folder,
                                       const std::string& expected_id) const {
  // If the overlay folder is not present, expected id must be empty.
  if (IO::access(overlay_folder, F_OK) != 0) {
    return expected_id.empty();
  }

  // If the overlay folder is present, the correct id must be present.
  return Overlay::Exists(overlay_folder, expected_id);
}
}  // namespace deploy
