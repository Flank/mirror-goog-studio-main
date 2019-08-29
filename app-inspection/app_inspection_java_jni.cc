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

namespace profiler {

void EnqueueAppInspectionServiceResponse(JNIEnv *env, int32_t command_id,
                                         ServiceResponse::Status status,
                                         jstring error_message) {
  profiler::JStringWrapper message(env, error_message);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[command_id, status, message](profiler::proto::AgentService::Stub &stub,
                                     grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION);
        event->set_is_ended(true);
        event->set_command_id(command_id);
        auto *inspection_event = event->mutable_app_inspection_event();
        auto *service_response = inspection_event->mutable_response();
        service_response->set_status(status);
        service_response->set_error_message(message.get().c_str());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

void EnqueueAppInspectionRawEvent(JNIEnv *env, int32_t command_id,
                                  jbyteArray event_data, int32_t length,
                                  jstring inspector_id) {
  profiler::JByteArrayWrapper data(env, event_data, length);
  profiler::JStringWrapper id(env, inspector_id);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[command_id, data, id](profiler::proto::AgentService::Stub &stub,
                              grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::APP_INSPECTION);
        event->set_is_ended(true);
        event->set_command_id(command_id);
        auto *inspection_event = event->mutable_app_inspection_event();
        auto *raw_response = inspection_event->mutable_raw_event();
        raw_response->set_inspector_id(id.get().c_str());
        raw_response->set_content(data.get());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

}  // namespace profiler

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_replyError(
    JNIEnv *env, jobject obj, jint command_id, jstring error_message) {
  profiler::EnqueueAppInspectionServiceResponse(
      env, command_id, ServiceResponse::ERROR, error_message);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_replySuccess(
    JNIEnv *env, jobject obj, jint command_id) {
  profiler::EnqueueAppInspectionServiceResponse(
      env, command_id, ServiceResponse::SUCCESS, nullptr);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_app_inspection_Responses_sendEvent(
    JNIEnv *env, jobject obj, jint command_id, jbyteArray event_data,
    jint length, jstring inspector_id) {
  profiler::EnqueueAppInspectionRawEvent(env, command_id, event_data, length,
                                         inspector_id);
}
}
