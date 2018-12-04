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

#include "tools/base/deploy/agent/native/swapper.h"

#include <unistd.h>

#include "tools/base/deploy/agent/native/hotswap.h"
#include "tools/base/deploy/agent/native/instrumenter.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/proto/deploy.pb.h"

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
    LogEvent("Could not read request from socket");
    return;
  }

  request_ = std::unique_ptr<proto::SwapRequest>(new proto::SwapRequest());
  if (!request_->ParseFromString(request_bytes)) {
    LogEvent("Could not parse swap request");
    return;
  }

  if (!InstrumentApplication(jvmti_, jni, request_->package_name())) {
    LogEvent("Could not instrument application");
    return;
  }

  HotSwap code_swap(jvmti_, jni);

  proto::AgentSwapResponse response;
  response.set_pid(getpid());

  SwapResult result = code_swap.DoHotSwap(*request_);
  if (!result.success) {
    response.set_status(proto::AgentSwapResponse::ERROR);
    response.set_jvmti_error_code(result.error_code);
    for (auto& details : result.error_details) {
      *response.add_jvmti_error_details() = details;
    }
  } else {
    response.set_status(proto::AgentSwapResponse::OK);
    LogEvent("Swap was successful");
  }

  // Prepare the instrumented code to restart after the package installation (if
  // a restart was requested).
  if (response.status() == proto::AgentSwapResponse::OK) {
    JniClass instrument(
        jni,
        "com/android/tools/deploy/instrument/ActivityThreadInstrumentation");
    jvalue arg{.z = request_->restart_activity()};
    instrument.CallStaticMethod<void>({"setRestart", "(Z)V"}, &arg);
  }

  SendResponse(response);
  Reset();
}

void Swapper::Reset() {
  socket_.reset(nullptr);
  request_.reset(nullptr);

  // This relinquishes all capabilities.
  jvmti_->DisposeEnvironment();
  jvmti_ = nullptr;
}

void Swapper::SendResponse(proto::AgentSwapResponse& response) {
  // Convert all events to proto events.
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response.add_events());
  }
  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket_->Write(response_bytes);
}

}  // namespace deploy
