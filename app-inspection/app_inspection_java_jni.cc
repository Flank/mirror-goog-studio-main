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
#include <jni.h>
#include "agent/agent.h"
#include "agent/jni_wrappers.h"

using app::inspection::ServiceResponse;

/** Stub implementation of jni method that always sends error response. */
extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_AppInspectionService_sendServiceResponseStub(
    JNIEnv *env, jobject obj) {
  profiler::Agent::Instance().SubmitAgentTasks(
      {[](profiler::proto::AgentService::Stub &stub,
          grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION);
        event->set_is_ended(true);
        auto *inspection_event = event->mutable_app_inspection_event();
        auto *service_response = inspection_event->mutable_response();
        service_response->set_status(ServiceResponse::ERROR);
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}
}