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

#include <signal.h>
#include <sys/wait.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/sites/sites.h"

using ServerResponse = proto::InstallServerResponse;

namespace deploy {

InstallClient::InstallClient(const std::string& package_name,
                             const std::string& serverBinaryPath,
                             const std::string& version, Executor& executor)
    : package_name_(package_name),
      server_binary_path_(serverBinaryPath),
      version_(version),
      executor_(package_name, executor) {}

InstallClient::~InstallClient() { StopServer(); }

std::string InstallClient::AppServerPath() {
  return Sites::AppCodeCache(package_name_) + kInstallServer + "-" + version_;
}

bool InstallClient::SpawnServer() {
  Phase p("InstallClient::SpawnServer");

  const std::string& server_path = AppServerPath();
  if (!executor_.ForkAndExec(server_path, {package_name_}, &output_fd_,
                             &input_fd_, &err_fd_, &server_pid_)) {
    ErrEvent("SpawnServer failed to fork and exec");
    return false;
  }
  return true;
}

bool InstallClient::StartServer() {
  Phase p("InstallClient::StartServer");

  StopServer();
  if (!SpawnServer()) {
    ErrEvent("Unable to bring up AppServer");
    return false;
  }
  return true;
}

void InstallClient::StopServer() {
  Phase p("InstallClient::StopServer");

  ResetFD(&output_fd_);
  ResetFD(&input_fd_);
  ResetFD(&err_fd_);

  if (server_pid_ != UNINITIALIZED) {
    LogEvent("kill(" + to_string(server_pid_) +
             ") this=" + to_string(getpid()));
    kill(server_pid_, SIGKILL);
    waitpid(server_pid_, nullptr, WNOHANG);

    server_pid_ = UNINITIALIZED;
  }
}

void InstallClient::ResetFD(int* fd) {
  if (*fd == UNINITIALIZED) {
    return;
  }
  close(*fd);
  *fd = UNINITIALIZED;
}

bool InstallClient::CopyServer() {
  Phase p("InstallClient::CopyServer");
  const std::string& src = server_binary_path_;
  const std::string& dst = AppServerPath();

  std::string cp_output;
  std::string cp_error;
  // Use -n to no-clobber and improve runtime.
  if (executor_.Run("cp", {"-n", src, dst}, &cp_output, &cp_error)) {
    return true;
  }

  // We don't need to check the output of this. It will fail if the code_cache
  // already exists; if the code_cache doesn't exist and we can't create it,
  // that failure will be caught when we try to copy the server.
  executor_.Run("mkdir", {"-p", Sites::AppCodeCache(package_name_)}, nullptr,
                nullptr);

  // Use -n to no-clobber improve runtime.
  if (executor_.Run("cp", {"-n", src, dst}, &cp_output, &cp_error)) {
    return true;
  }

  ErrEvent("InstallClient: Could not copy '" + src + "' to '" + dst +
           ": out='" + cp_output + "', err='" + cp_error + "'");
  return false;
}

void InstallClient::RetrieveErr() const {
  fcntl(err_fd_, F_SETFL, O_NONBLOCK);
  constexpr size_t txt_size = 128;
  char* txt = new char[txt_size]();
  int size = read(err_fd_, txt, txt_size - 1);
  if (size > 0) {
    std::string msg(txt, 0, size);
    ErrEvent(msg);
  }
  delete[] txt;
}

std::unique_ptr<proto::InstallServerResponse> InstallClient::Send(
    proto::InstallServerRequest& req) {
  // # 1 Write to the pipe, without knowing if the other end is live.
  auto resp = SendOnce(req);
  if (resp != nullptr) {
    return resp;
  }

  // #2 Was the other end terminated by Android platform?
  // Let's just try to start it and Send again.
  if (!StartServer()) {
    return nullptr;
  }
  resp = SendOnce(req);
  if (resp != nullptr) {
    return resp;
  }

  // #3 The binary is likely missing. Copy it, start the server
  // and attempt to Send again.
  CopyServer();
  if (!StartServer()) {
    return nullptr;
  }
  resp = SendOnce(req);
  if (resp == nullptr) {
    RetrieveErr();
  }

  return resp;
}

std::unique_ptr<proto::InstallServerResponse> InstallClient::SendOnce(
    proto::InstallServerRequest& req) {
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

// Writes a serialized protobuf message to the connected client.
bool InstallClient::Write(const proto::InstallServerRequest& request) const {
  ProtoPipe output(output_fd_);
  return output.Write(request);
}

bool InstallClient::Read(proto::InstallServerResponse* response) {
  ProtoPipe input(input_fd_);
  if (input.Read(kDefaultTimeoutMs, response)) {
    // Convert remote events to local events.
    for (int i = 0; i < response->events_size(); i++) {
      const proto::Event& event = response->events(i);
      AddRawEvent(ConvertProtoEventToEvent(event));
    }
    return true;
  }
  return false;
}
}  // namespace deploy