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

}  // namespace deploy