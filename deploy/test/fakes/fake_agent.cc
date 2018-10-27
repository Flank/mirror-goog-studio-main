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

#include "tools/base/deploy/test/fakes/fake_agent.h"

namespace deploy {

bool FakeAgent::Connect(const std::string& socket_address) {
  return socket_.Open() && socket_.Connect(socket_address);
}

bool FakeAgent::RespondSuccess() {
  proto::AgentSwapResponse response;
  response.set_pid(pid_);
  response.set_status(proto::AgentSwapResponse::OK);

  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  return socket_.Write(response_bytes);
}

bool FakeAgent::RespondFailure() {
  proto::AgentSwapResponse response;
  response.set_pid(pid_);
  response.set_status(proto::AgentSwapResponse::ERROR);

  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  return socket_.Write(response_bytes);
}

// TODO(noahz): Refactor protocol logic out of sockets/messagepipewrappers to
// allow this to *actually* test a crash.
bool FakeAgent::RespondCrash() {
  proto::AgentSwapResponse response;
  response.set_pid(pid_);
  response.set_status(proto::AgentSwapResponse::ERROR);

  std::string response_bytes;
  response.SerializeToString(&response_bytes);

  // Write half the message before exiting.
  response_bytes = response_bytes.substr(0, response_bytes.size() / 2);

  if (!socket_.Write(response_bytes)) {
    return false;
  }

  Exit();
  return true;
}

bool FakeAgent::ReceiveMessage(proto::SwapRequest* request) {
  std::string request_bytes;
  return socket_.Read(&request_bytes) &&
         request->ParseFromString(request_bytes);
}

void FakeAgent::Exit() { socket_.Close(); }

}  // namespace deploy
