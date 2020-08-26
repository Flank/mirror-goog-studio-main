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
#include "tools/base/deploy/agent/native/crash_logger.h"
#include "tools/base/deploy/agent/native/instrumenter.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
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
                  proto::AgentResponse& response) {
  // Convert all events to proto events.
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response.add_events());
  }

  response.set_pid(getpid());

  if (response.has_swap_response() &&
      response.swap_response().status() != proto::AgentSwapResponse::OK) {
    response.set_status(proto::AgentResponse::SWAP_FAILURE);
  } else {
    response.set_status(proto::AgentResponse::OK);
  }

  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket.Write(response_bytes);
}

void SendFailure(const deploy::Socket& socket,
                 proto::AgentResponse::Status status) {
  proto::AgentResponse response;
  response.set_pid(getpid());
  response.set_status(status);
  std::string response_bytes;
  response.SerializeToString(&response_bytes);
  socket.Write(response_bytes);
}

jint HandleStartupAgent(JavaVM* vm, const std::string& app_data_dir) {
  jvmtiEnv* jvmti;
  JNIEnv* jni;

  Log::V("Startup agent attached to VM");

  // Set up the JVMTI and JNI environments.

  if (vm->GetEnv((void**)&jni, JNI_VERSION_1_2) != JNI_OK) {
    ErrEvent("Error retrieving JNI function table.");
    return JNI_OK;
  }

  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    ErrEvent("Error retrieving JVMTI function table.");
    return JNI_OK;
  }

  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    ErrEvent("Error setting capabilities.");
    jvmti->DisposeEnvironment();
    return JNI_OK;
  }

  const std::string package_name =
      app_data_dir.substr(app_data_dir.find_last_of('/') + 1);

  CrashLogger::Instance().Initialize(package_name,
                                     proto::AgentExceptionLog::STARTUP_AGENT);

  if (!InstrumentApplication(jvmti, jni, package_name, true)) {
    ErrEvent("Could not instrument application");
    jvmti->DisposeEnvironment();
    return JNI_OK;
  }

  jvmti->DisposeEnvironment();
  return JNI_OK;
}

jint HandleAgentRequest(JavaVM* vm, char* socket_name) {
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

  if (!socket.Connect(socket_name)) {
    ErrEvent("Could not connect to socket");
    return JNI_OK;
  }

  // Read the request from the socket.
  std::string request_bytes;
  if (!socket.Read(&request_bytes)) {
    ErrEvent("Could not read request from socket");
    SendFailure(socket, proto::AgentResponse::SOCKET_READ_FAILED);
    return JNI_OK;
  }

  proto::AgentRequest request;
  if (!request.ParseFromString(request_bytes)) {
    ErrEvent("Could not parse swap request");
    SendFailure(socket, proto::AgentResponse::REQUEST_PARSE_FAILED);
    return JNI_OK;
  }

  // Set up the JVMTI and JNI environments. Regardless of what
  // the agent is about to perform.
  if (vm->GetEnv((void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK) {
    ErrEvent("Error retrieving JVMTI function table.");
    SendFailure(socket, proto::AgentResponse::JVMTI_SETUP_FAILED);
    return JNI_OK;
  }

  if (vm->GetEnv((void**)&jni, JNI_VERSION_1_2) != JNI_OK) {
    ErrEvent("Error retrieving JNI function table.");
    SendFailure(socket, proto::AgentResponse::JNI_SETUP_FAILED);
    return JNI_OK;
  }

  // Try to add capabilities needed. Swap and Live Literal probably have
  // different capabilities requirement. However, we are not going to
  // be targeting certain Android device with one but not the other so
  // we are going to request all the capabilities we ever use at once here.
  if (jvmti->AddCapabilities(&REQUIRED_CAPABILITIES) != JVMTI_ERROR_NONE) {
    ErrEvent("Error setting capabilities.");
    SendFailure(socket, proto::AgentResponse::SET_CAPABILITIES_FAILED);
    jvmti->DisposeEnvironment();
    return JNI_OK;
  }

  proto::AgentResponse response;
  if (request.has_swap_request()) {
    // Only initialize exception logging if we're on R+, for now.
    proto::SwapRequest swap_request = request.swap_request();
    if (swap_request.overlay_swap()) {
      auto agent_purpose = swap_request.restart_activity()
                               ? proto::AgentExceptionLog::APPLY_CHANGES
                               : proto::AgentExceptionLog::APPLY_CODE_CHANGES;
      CrashLogger::Instance().Initialize(swap_request.package_name(),
                                         agent_purpose);
    }

    *response.mutable_swap_response() =
        Swapper::Instance().Swap(jvmti, jni, swap_request);
    SendResponse(socket, response);
  } else if (request.has_live_literal_request()) {
    // TDOO: For now just do a printf and the test will verify if we get to this
    // stage.
    jclass syscls = jni->FindClass("java/lang/System");
    jfieldID fid =
        jni->GetStaticFieldID(syscls, "out", "Ljava/io/PrintStream;");
    jobject out = jni->GetStaticObjectField(syscls, fid);
    jclass pscls = jni->FindClass("java/io/PrintStream");
    jmethodID mid = jni->GetMethodID(pscls, "println", "(Ljava/lang/String;)V");
    jni->CallVoidMethod(out, mid,
                        jni->NewStringUTF("Live Literal Update on VM"));
  } else {
    Log::E("Unknown / Empty Agent Request");
  }

  // We return JNI_OK even if anything failed, since returning JNI_ERR just
  // causes ART to attempt to re-attach the agent with a null classloader.
  jvmti->DisposeEnvironment();
  return JNI_OK;
}

// Event that fires when the agent hooks onto a running VM.
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* input,
                                                 void* reserved) {
  // Startup agents are passed the path to the app data directory.
  // TODO(b/148544245): See if there's a nicer way to do this.
  if (input[0] == '/') {
    return HandleStartupAgent(vm, input);
  } else {
    return HandleAgentRequest(vm, input);
  }
}

}  // namespace deploy
