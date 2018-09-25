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
#include "utils/log.h"

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

  if (request_->restart_activity()) {
    if (!InstrumentApplication(jvmti_, jni, request_->package_name())) {
      Log::E("Could not instrument application");
      return;
    }

    proto::SwapResponse response;
    response.set_pid(getpid());
    response.set_status(proto::SwapResponse::NEED_ACTIVITY_RESTART);
    SendResponse(response);
    return;
  }

  FinishSwap(jni);
}

bool Swapper::FinishSwap(JNIEnv* jni) {
  HotSwap code_swap(jvmti_, jni);

  proto::SwapResponse response;
  response.set_pid(getpid());

  if (!code_swap.DoHotSwap(*request_, response.mutable_error_details())) {
    response.set_status(proto::SwapResponse::ERROR);
  } else {
    response.set_status(proto::SwapResponse::OK);
  }

  SendResponse(response);
  Reset();

  return response.status() == proto::SwapResponse::OK;
}

void Swapper::Reset() {
  socket_.reset(nullptr);
  request_.reset(nullptr);

  // This relinquishes all capabilities.
  jvmti_->DisposeEnvironment();
  jvmti_ = nullptr;
}

void Swapper::SendResponse(const proto::SwapResponse& response) {
  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket_->Write(response_bytes);
}

}  // namespace deploy
