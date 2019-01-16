/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include <jni.h>
#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"

/**
 * This is the native implentation for EchoService::sendEchoMessage(String).
 * This function creates an Event populates the echo data and sends the event
 * via the AgentService::SendEvent function.
 */
extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_echo_EchoService_sendEchoMessage(
    JNIEnv *env, jclass clazz, jstring jmessage) {
  profiler::JStringWrapper message(env, jmessage);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[message](profiler::proto::AgentService::Stub &stub,
                 grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        // Create Event.
        auto *event = request.mutable_event();
        event->set_is_ended(true);
        event->set_kind(profiler::proto::Event::ECHO);
        // Set echo data on event.
        auto *echo = event->mutable_echo();
        echo->set_data(message.get().c_str());
        // Send event to daemon via grpc.
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}
}