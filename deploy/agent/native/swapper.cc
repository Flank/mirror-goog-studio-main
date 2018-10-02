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
 *
 */

#include "swapper.h"

#include <unistd.h>
#include "deploy.pb.h"
#include "hotswap.h"
#include "instrumenter.h"
#include "socket.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

Swapper::Swapper() : jvmti_(nullptr) {}

Swapper* Swapper::instance_ = nullptr;

Swapper& Swapper::Instance() {
  if (instance_ == nullptr) {
    instance_ = new Swapper();
  }
  return *instance_;
}

void Swapper::Initialize(jvmtiEnv* jvmti, std::unique_ptr<Socket> socket) {
  // Always reset first, to make sure that anything the previous agent left
  // behind is cleaned up.
  if (jvmti_ != nullptr) {
    Reset();
  }

  jvmti_ = jvmti;
  socket_ = std::move(socket);
}

void Swapper::StartSwap(JNIEnv* jni) {
  std::string request_bytes;
  if (!socket_->Read(&request_bytes)) {
    Log::E("Could not read request from socket");
    return;
  }

  request_ = std::unique_ptr<proto::SwapRequest>(new proto::SwapRequest());
  if (!request_->ParseFromString(request_bytes)) {
    request_.reset();
    Log::E("Could not parse swap request");
    return;
  }

  FinishSwap(jni);
}

bool Swapper::FinishSwap(JNIEnv* jni) {
  HotSwap code_swap(jvmti_, jni);

  proto::AgentSwapResponse response;
  response.set_pid(getpid());

  std::string error_message;
  if (!code_swap.DoHotSwap(*request_, &error_message)) {
    response.set_status(proto::AgentSwapResponse::ERROR);
    ErrEvent(response.add_events(), error_message);
    SendResponse(response);
  } else {
    LogEvent(response.add_events(), "Swap was successful");
    response.set_status(proto::AgentSwapResponse::OK);
    SendResponse(response);
    if (request_->restart_activity()) {
      // Wait for the installer to request the activity restart
      // Note that this will BLOCK the main thread until the
      // installer finishes the installation and issues a
      // reload-appinfo command.
      std::string resume;
      if (!socket_->Read(&resume)) {
        Log::E("Could not read resume request from socket");
      }
    }
  }

  Reset();

  return response.status() == proto::AgentSwapResponse::OK;
}

void Swapper::Reset() {
  socket_.reset(nullptr);
  request_.reset(nullptr);

  // This relinquishes all capabilities.
  jvmti_->DisposeEnvironment();
  jvmti_ = nullptr;
}

void Swapper::SendResponse(const proto::AgentSwapResponse& response) {
  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket_->Write(response_bytes);
}

}  // namespace deploy
