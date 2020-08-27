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
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/overlay/overlay.h"

using ServerRequest = proto::InstallServerRequest;
using ServerResponse = proto::InstallServerResponse;

namespace deploy {

namespace {

enum class StartResult { SUCCESS, TRY_COPY, FAILURE };

// Attempts to start the server and connect an InstallClient to it. If this
// operation sets the value of result to SUCCESS, returns a unique_ptr to an
// InstallClient; otherwise the pointer is nullptr.
std::unique_ptr<InstallClient> TryStartServer(const Executor& executor,
                                              const std::string& exec_path,
                                              StartResult* result) {
  int stdin_fd;
  int stdout_fd;
  int stderr_fd;
  int pid;
  if (!executor.ForkAndExec(exec_path, {}, &stdin_fd, &stdout_fd, &stderr_fd,
                            &pid)) {
    // ForkAndExec only returns false if the pipe creation fails.
    ErrEvent("Could not ForkAndExec when starting server");
    *result = StartResult::FAILURE;
    return nullptr;
  }

  // Wait for server startup acknowledgement. Note that when creating the
  // client, the server's output is the clients's input and vice-versa.
  std::unique_ptr<InstallClient> client(new InstallClient(stdout_fd, stdin_fd));
  if (client->WaitForStart()) {
    close(stderr_fd);
    *result = StartResult::SUCCESS;
    return client;
  }

  // The server failed to start, so wait for the process to exit.
  waitpid(pid, nullptr, 0);

  // If no server startup ack is present, read from stderr. The server never
  // writes to stderr, so we know that anything in stderr is from run-as.
  char err_buffer[128] = {'\0'};
  ssize_t count = read(stderr_fd, err_buffer, 127);

  close(stdout_fd);
  close(stderr_fd);

  if (count > 0) {
    std::string error_message(err_buffer, 0, count);
    ErrEvent("Unable to startup install-server, output: '" + error_message +
             "'");
  }

  *result = StartResult::FAILURE;
  return nullptr;
}
}  // namespace

void InstallServer::Run() {
  Acknowledge();
  Pump();

  ServerResponse response;

  // Consume traces and proto events.
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response.add_events());
  }

  // Send the final server response.
  response.set_status(ServerResponse::SERVER_EXITED);
  if (!output_.Write(response)) {
    ErrEvent("Could not write server exit message");
  }
}

void InstallServer::Acknowledge() {
  Phase("InstallServer::Acknowledge");
  ServerResponse response;

  response.set_status(ServerResponse::SERVER_STARTED);
  if (!output_.Write(response)) {
    ErrEvent("Could not write server start message");
    return;
  }
}

void InstallServer::Pump() {
  Phase("InstallServer::Pump");
  ServerRequest request;
  while (input_.Read(-1, &request)) {
    if (request.type() == ServerRequest::HANDLE_REQUEST) {
      HandleRequest(request);
    } else if (request.type() == ServerRequest::SERVER_EXIT) {
      break;
    }
  }
}

void InstallServer::HandleRequest(const ServerRequest& request) {
  ServerResponse response;

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
  }

  response.set_status(ServerResponse::REQUEST_COMPLETED);
  output_.Write(response);
}

void InstallServer::HandleOpenSocket(
    const proto::OpenAgentSocketRequest& request,
    proto::OpenAgentSocketResponse* response) {
  if (agent_server_.Open() &&
      agent_server_.BindAndListen(request.socket_name())) {
    response->set_status(proto::OpenAgentSocketResponse::OK);
  } else {
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
    std::unique_ptr<Socket> agent(new Socket);
    // 15 seconds since there is a chance we need to wait for the
    // host to attach an agent from the debugger.
    if (!agent_server_.Accept(agent.get(), 15000)) {
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
  const std::string overlay_folder = request.overlay_path() + "/.overlay"_s;

  if (request.wipe_all_files()) {
    if (nftw(
            overlay_folder.c_str(),
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
  const std::string log_dir = GetAgentExceptionLogDir(request.package_name());
  DIR* dir = IO::opendir(log_dir);
  if (dir == nullptr) {
    return;
  }
  dirent* entry;
  while ((entry = readdir(dir)) != nullptr) {
    if (entry->d_type != DT_REG) {
      continue;
    }
    const std::string log_path = log_dir + "/" + entry->d_name;
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

namespace {
bool TryCopyServer(const RunasExecutor& run_as, const std::string& server_path,
                   const std::string& exec_path, const std::string& exec_name) {
  std::string cp_output;
  std::string cp_error;
  if (run_as.Run("cp", {server_path, exec_path + exec_name}, &cp_output,
                 &cp_error)) {
    return true;
  }

  // We don't need to check the output of this. It will fail if the code_cache
  // already exists; if the code_cache doesn't exist and we can't create it,
  // that failure will be caught when we try to copy the server.
  run_as.Run("mkdir", {exec_path}, nullptr, nullptr);

  if (run_as.Run("cp", {server_path, exec_path + exec_name}, &cp_output,
                 &cp_error)) {
    return true;
  }

  ErrEvent("Could not copy binary: " + cp_error);
  return false;
}
}  // namespace

bool InstallServer::DoesOverlayIdMatch(const std::string& overlay_folder,
                                       const std::string& expected_id) const {
  // If the overlay folder is not present, expected id must be empty.
  if (IO::access(overlay_folder, F_OK) != 0) {
    return expected_id.empty();
  }

  // If the overlay folder is present, the correct id must be present.
  return Overlay::Exists(overlay_folder, expected_id);
}

std::unique_ptr<InstallClient> StartInstallServer(
    Executor& executor, const std::string& server_path,
    const std::string& package_name, const std::string& exec_name) {
  Phase p("InstallServer::StartServer");
  const std::string exec_path = "/data/data/" + package_name + "/code_cache/";
  const RunasExecutor run_as(package_name, executor);

  StartResult result;
  auto client = TryStartServer(run_as, exec_path + exec_name, &result);
  if (result == StartResult::SUCCESS) {
    return client;
  }

  if (TryCopyServer(run_as, server_path, exec_path, exec_name)) {
    return TryStartServer(run_as, exec_path + exec_name, &result);
  }

  return nullptr;
}

}  // namespace deploy
