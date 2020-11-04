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

#ifndef INSTALL_SERVER_H
#define INSTALL_SERVER_H

#include <memory>
#include <string>

#include "tools/base/deploy/common/proto_pipe.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/installer/server/canary.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// Object that can be used to run an install server in the current process.
class InstallServer {
 public:
  InstallServer(int input_fd, int output_fd, const Canary& canary)
      : input_(input_fd), output_(output_fd), canary_(canary) {}

  ~InstallServer();

  // Runs an install server in this process. This blocks until the server
  // finishes running.
  void Run();

 private:
  ProtoPipe input_;
  ProtoPipe output_;
  Socket agent_server_;
  const Canary& canary_;

  void HandleRequest(const proto::InstallServerRequest& request);

  void HandleCheckSetup(const proto::CheckSetupRequest& request,
                        proto::CheckSetupResponse* response) const;

  void HandleOverlayUpdate(const proto::OverlayUpdateRequest& request,
                           proto::OverlayUpdateResponse* response) const;

  void HandleGetAgentExceptionLog(
      const proto::GetAgentExceptionLogRequest& request,
      proto::GetAgentExceptionLogResponse* response) const;

  // Opens a socket to listen for agent connections. The opened socket is closed
  // by HandleSendMessage.
  void HandleOpenSocket(const proto::OpenAgentSocketRequest& request,
                        proto::OpenAgentSocketResponse* response);

  // Waits for a number of agents to connect to the socket, sends them a
  // message, and collects their responses. This method also closes the socket.
  void HandleSendMessage(const proto::SendAgentMessageRequest& request,
                         proto::SendAgentMessageResponse* response);
  void HandleSendMessageInner(const proto::SendAgentMessageRequest& request,
                              proto::SendAgentMessageResponse* response);

  bool DoesOverlayIdMatch(const std::string& overlay_folder,
                          const std::string& expected_id) const;

  // Close input and output stream
  void Close();
};

}  // namespace deploy

#endif