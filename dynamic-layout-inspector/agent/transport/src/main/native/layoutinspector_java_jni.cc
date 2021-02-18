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
#include <unistd.h>
#include <sstream>
#include "agent/agent.h"
#include "agent/jni_wrappers.h"
#include "utils/agent_task.h"

using layoutinspector::ComponentTreeEvent;
using layoutinspector::ComponentTreeEvent_PayloadType;
using layoutinspector::PropertyEvent;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::proto::AgentService;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::SendEventRequest;

/**
 * Native calls to send the skia picture back to studio (using an event with a
 * payload id and, separately, a payload), and error messages.
 */
extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_sendErrorMessage(
    JNIEnv *env, jclass clazz, jstring jmessage) {
  profiler::JStringWrapper message(env, jmessage);
  profiler::Agent::Instance().SubmitAgentTasks(
      {[message](profiler::proto::AgentService::Stub &stub,
                 grpc::ClientContext &ctx) mutable {
        profiler::proto::SendEventRequest request;
        auto *event = request.mutable_event();
        event->set_kind(profiler::proto::Event::LAYOUT_INSPECTOR);
        event->set_pid(getpid());
        event->set_group_id(profiler::proto::Event::LAYOUT_INSPECTOR_ERROR);
        auto *inspector_event = event->mutable_layout_inspector_event();
        inspector_event->set_error_message(message.get().c_str());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_allocateSendRequest(
    JNIEnv *env, jclass clazz) {
  SendEventRequest *request = new SendEventRequest();
  return (long)request;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_freeSendRequest(
    JNIEnv *env, jclass clazz, jlong jrequest) {
  if (jrequest != 0L) {
    delete (SendEventRequest *)jrequest;
  }
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_initComponentTree(
    JNIEnv *env, jclass clazz, jlong jrequest, jlongArray allWindowIds,
    jint rootOffsetX, jint rootOffsetY) {
  SendEventRequest *request = (SendEventRequest *)jrequest;
  auto *event = request->mutable_event();
  auto *inspector_event = event->mutable_layout_inspector_event();
  auto *tree = inspector_event->mutable_tree();
  jsize windowCount = env->GetArrayLength(allWindowIds);
  jlong *windowIds = env->GetLongArrayElements(allWindowIds, 0);
  for (int i = 0; i < windowCount; i++) {
    tree->add_all_window_ids(windowIds[i]);
  }
  env->ReleaseLongArrayElements(allWindowIds, windowIds, 0);
  tree->set_root_surface_offset_x(rootOffsetX);
  tree->set_root_surface_offset_y(rootOffsetY);

  return (long)tree;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_sendComponentTree(
    JNIEnv *env, jclass clazz, jlong jrequest, jbyteArray jmessage, jint jlen,
    jint id, jint imageType, jint generation) {
  ComponentTreeEvent_PayloadType payloadType =
      ComponentTreeEvent_PayloadType(imageType);
  if (payloadType != ComponentTreeEvent::NONE) {
    profiler::JByteArrayWrapper message(env, jmessage, jlen);

    std::stringstream ss;
    ss << id;
    std::string payload_name = ss.str();

    // Note: property_event is copied by value here which is not optimal.
    Agent::Instance().SubmitAgentTasks(profiler::CreateTasksToSendPayload(
        payload_name, std::string(message.get().data(), message.length()),
        true));
  }

  SendEventRequest request;
  request = *((SendEventRequest *)jrequest);

  Agent::Instance().SubmitAgentTasks(
      {[request, id, payloadType, generation](
           AgentService::Stub &stub, grpc::ClientContext &ctx) mutable {
        auto *event = request.mutable_event();
        auto *inspector_event = event->mutable_layout_inspector_event();
        auto *tree = inspector_event->mutable_tree();
        tree->set_payload_id(id);
        tree->set_payload_type(payloadType);
        tree->set_generation(generation);
        event->set_pid(getpid());
        event->set_is_ended(true);
        event->set_kind(Event::LAYOUT_INSPECTOR);
        event->set_group_id(Event::COMPONENT_TREE);
        EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}
}