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

#ifndef FAKE_AGENT_H
#define FAKE_AGENT_H

#include <memory>
#include <string>

#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class FakeAgent {
 public:
  FakeAgent(int pid) : pid_(pid) {}

  // Agent connects to the socket at the specified address.
  bool Connect(const std::string& socket_address);

  // Agent responds with a swap-success message.
  bool RespondSuccess();

  // Agent responds with a swap-failure message.
  bool RespondFailure();

  // Agent crashes partway through the response.
  bool RespondCrash();

  // Agent blocks until receiving a complete swap request.
  bool ReceiveMessage(proto::SwapRequest* request);

  // Agent crashes without sending any messages.
  void Exit();

 private:
  int pid_;
  deploy::Socket socket_;
};

}  // namespace deploy

#endif
