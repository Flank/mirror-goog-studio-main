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

#include "tools/base/deploy/installer/server/install_client.h"

#include <sys/wait.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"

using ServerResponse = proto::InstallServerResponse;

namespace deploy {

bool InstallClient::WaitForStatus(ServerResponse::Status status) {
  ServerResponse message;
  if (!Read(&message)) {
    ErrEvent("Expected server status "_s + to_string(status) +
             " but did not receive a response.");
    return false;
  }
  if (message.status() != status) {
    ErrEvent("Expected server status "_s + to_string(status) +
             " but received status " + to_string(message.status()));
    return false;
  }
  return true;
}

bool InstallClient::KillServerAndWait(proto::InstallServerResponse* response) {
  proto::InstallServerRequest request;
  request.set_type(proto::InstallServerRequest::SERVER_EXIT);
  if (!Write(request)) {
    return false;
  }
  output_.Close();

  int status;
  waitpid(server_pid_, &status, 0);
  return Read(response);
}

// TODO: This is where we will retry with install_serverd
std::unique_ptr<proto::InstallServerResponse> InstallClient::Send(
    proto::InstallServerRequest& req) {
  req.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  if (!Write(req)) {
    return nullptr;
  }

  std::unique_ptr<proto::InstallServerResponse> resp(
      new proto::InstallServerResponse);
  if (!Read(resp.get())) {
    return nullptr;
  }

  if (resp->status() != proto::InstallServerResponse::REQUEST_COMPLETED) {
    return nullptr;
  }

  return resp;
}

std::unique_ptr<proto::CheckSetupResponse> InstallClient::CheckSetup(
    const proto::CheckSetupRequest& req) {
  proto::InstallServerRequest request;
  *request.mutable_check_request() = req;
  auto resp = Send(request);
  if (resp == nullptr || !resp->has_check_response()) {
    return nullptr;
  }
  return std::unique_ptr<proto::CheckSetupResponse>(
      resp->release_check_response());
}

std::unique_ptr<proto::OverlayUpdateResponse> InstallClient::UpdateOverlay(
    const proto::OverlayUpdateRequest& req) {
  proto::InstallServerRequest request;
  *request.mutable_overlay_request() = req;
  auto resp = Send(request);
  if (resp == nullptr || !resp->has_overlay_response()) {
    return nullptr;
  }
  return std::unique_ptr<proto::OverlayUpdateResponse>(
      resp->release_overlay_response());
}

std::unique_ptr<proto::GetAgentExceptionLogResponse>
InstallClient::GetAgentExceptionLog(
    const proto::GetAgentExceptionLogRequest& req) {
  proto::InstallServerRequest request;
  *request.mutable_log_request() = req;
  auto resp = Send(request);
  if (resp == nullptr || !resp->has_log_response()) {
    return nullptr;
  }
  return std::unique_ptr<proto::GetAgentExceptionLogResponse>(
      resp->release_log_response());
}

std::unique_ptr<proto::OpenAgentSocketResponse> InstallClient::OpenAgentSocket(
    const proto::OpenAgentSocketRequest& req) {
  proto::InstallServerRequest request;
  *request.mutable_socket_request() = req;
  auto resp = Send(request);
  if (resp == nullptr || !resp->has_socket_response()) {
    return nullptr;
  }
  return std::unique_ptr<proto::OpenAgentSocketResponse>(
      resp->release_socket_response());
}

std::unique_ptr<proto::SendAgentMessageResponse>
InstallClient::SendAgentMessage(const proto::SendAgentMessageRequest& req) {
  proto::InstallServerRequest request;
  *request.mutable_send_request() = req;
  auto resp = Send(request);
  if (resp == nullptr || !resp->has_send_response()) {
    return nullptr;
  }
  return std::unique_ptr<proto::SendAgentMessageResponse>(
      resp->release_send_response());
}

}  // namespace deploy