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

#include <jni.h>
#include <jvmti.h>
#include <unistd.h>

#include "tools/base/deploy/agent/native/capabilities.h"
#include "tools/base/deploy/agent/native/jni/jni_util.h"
#include "tools/base/deploy/agent/native/swapper.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

// Watch as I increment between runs!
static int run_counter = 0;

void SendResponse(const deploy::Socket& socket,
                  proto::AgentSwapResponse& response) {
  // Convert all events to proto events.
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response.add_events());
  }
  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket.Write(response_bytes);
}

void SendFailure(const deploy::Socket& socket,
                 proto::AgentSwapResponse::Status status) {
  proto::AgentSwapResponse response;
  response.set_pid(getpid());
  response.set_status(status);
  SendResponse(socket, response);
}

// Event that fires when the agent hooks onto a running VM.
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* input,
                                                 void* reserved) {
  jvmtiEnv* jvmti;
  JNIEnv* jni;

  InitEventSystem();

  Log::V("Prior agent invocations in this VM: %d", run_counter++);

  // Connect to the installer proxy server.

  deploy::Socket socket;
  if (!socket.Open()) {
    ErrEvent("Could not open new socket");
    return JNI_OK;
  }

  if (!socket.Connect(input)) {
    ErrEvent("Could not connect to socket");
    return JNI_OK;
  }

  // Read the swap request from the socket.

  std::string request_bytes;
  if (!socket.Read(&request_bytes)) {
    ErrEvent("Could not read request from socket");
    SendFailure(socket, proto::AgentSwapResponse::SOCKET_READ_FAILED);
    return JNI_OK;
  }

  proto::SwapRequest request;
  if (!request.ParseFromString(request_bytes)) {
    ErrEvent("Could not parse swap request");
    SendFailure(socket, proto::AgentSwapResponse::REQUEST_PARSE_FAILED);
    return JNI_OK;
  }

  // Set up the JVMTI and JNI environments.

  if (vm->GetEnv((void**)&jni, JNI_VERSION_1_2) != JNI_OK) {
    ErrEvent("Error retrieving JNI function table.");
    SendFailure(socket, proto::AgentSwapResponse::JNI_SETUP_FAILED);
    return JNI_OK;
  }

  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    ErrEvent("Error retrieving JVMTI function table.");
    SendFailure(socket, proto::AgentSwapResponse::JVMTI_SETUP_FAILED);
    return JNI_OK;
  }

  // Try to add capabilities needed for the swap, then swap.

  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    ErrEvent("Error setting capabilities.");
    SendFailure(socket, proto::AgentSwapResponse::SET_CAPABILITIES_FAILED);
  } else {
    proto::AgentSwapResponse response =
        Swapper::Instance().Swap(jvmti, jni, request);
    SendResponse(socket, response);
  }

  // We return JNI_OK even if the hot swap fails, since returning JNI_ERR just
  // causes ART to attempt to re-attach the agent with a null classloader.
  jvmti->DisposeEnvironment();
  return JNI_OK;
}

}  // namespace deploy
