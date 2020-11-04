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

#ifndef INSTALL_CLIENT_H
#define INSTALL_CLIENT_H

#include <memory>
#include <string>
#include <unordered_set>
#include <vector>

#include "tools/base/deploy/common/proto_pipe.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// Client object for communicating with an install server.
class InstallClient {
 public:
  InstallClient(const std::string& package_name,
                const std::string& serverBinaryPath, const std::string& version,
                Executor& executor = Executor::Get());
  ~InstallClient();

  std::unique_ptr<proto::CheckSetupResponse> CheckSetup(
      const proto::CheckSetupRequest& req);
  std::unique_ptr<proto::OverlayUpdateResponse> UpdateOverlay(
      const proto::OverlayUpdateRequest& req);
  std::unique_ptr<proto::GetAgentExceptionLogResponse> GetAgentExceptionLog(
      const proto::GetAgentExceptionLogRequest& req);
  std::unique_ptr<proto::OpenAgentSocketResponse> OpenAgentSocket(
      const proto::OpenAgentSocketRequest& req);
  std::unique_ptr<proto::SendAgentMessageResponse> SendAgentMessage(
      const proto::SendAgentMessageRequest& req);


 private:
  std::string AppServerPath();
  bool SpawnServer();
  bool StartServer();
  void StopServer();
  bool CopyServer();

  static constexpr auto UNINITIALIZED = -1;
  int server_pid_ = UNINITIALIZED;
  int output_fd_ = UNINITIALIZED;
  int input_fd_ = UNINITIALIZED;
  int err_fd_ = UNINITIALIZED;
  static void ResetFD(int* fd);
  void RetrieveErr() const;

  const int kDefaultTimeoutMs = 5000;

  // Send optimistically with increasingly expensive methods.
  // 1. Write request/ Read response to pipe.
  // 2. Start Server and do #1.
  // 3. Copy Server, and do #2.
  // 4. Fail.
  std::unique_ptr<proto::InstallServerResponse> Send(
      proto::InstallServerRequest& req);

  std::unique_ptr<proto::InstallServerResponse> SendOnce(
      proto::InstallServerRequest& req);

  const std::string package_name_;
  const std::string server_binary_path_;
  const std::string version_;
  RunasExecutor executor_;

  // Writes a serialized protobuf message to the connected client.
  bool Write(const proto::InstallServerRequest& request) const;

  // Waits up for a message to be available from the client, then attempts to
  // parse the data read into the specified proto.
  bool Read(proto::InstallServerResponse* response);
};

}  // namespace deploy

#endif