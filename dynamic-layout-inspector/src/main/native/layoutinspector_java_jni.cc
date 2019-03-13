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
#include <sstream>
#include "agent/agent.h"
#include "agent/jni_wrappers.h"

using layoutinspector::Property;
using layoutinspector::Property_Type;
using layoutinspector::PropertyEvent;
using layoutinspector::Resource;
using layoutinspector::StringEntry;
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
        event->set_group_id(profiler::proto::Event::LAYOUT_INSPECTOR_ERROR);
        auto *inspector_event = event->mutable_layout_inspector_event();
        inspector_event->set_error_message(message.get().c_str());
        profiler::proto::EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_sendSkiaPicture(
    JNIEnv *env, jclass clazz, jbyteArray jmessage, jint jlen, jint id) {
  profiler::JByteArrayWrapper message(env, jmessage, jlen);

  std::stringstream ss;
  ss << id;
  std::string payload_name = ss.str();

  profiler::Agent::Instance().SubmitAgentTasks(
      {[message, payload_name](profiler::proto::AgentService::Stub &stub,
                               grpc::ClientContext &ctx) mutable {
         profiler::proto::EmptyResponse response;
         profiler::proto::SendBytesRequest payload;
         payload.set_name(payload_name);
         payload.set_bytes(message.get().data(), message.length());
         return stub.SendBytes(&ctx, payload, &response);
       },
       [id](profiler::proto::AgentService::Stub &stub,
            grpc::ClientContext &ctx) mutable {
         profiler::proto::SendEventRequest request;
         auto *event = request.mutable_event();
         event->set_kind(profiler::proto::Event::LAYOUT_INSPECTOR);
         event->set_group_id(profiler::proto::Event::SKIA_PICTURE);
         auto *inspector_event = event->mutable_layout_inspector_event();
         inspector_event->set_payload_id(id);
         profiler::proto::EmptyResponse response;
         return stub.SendEvent(&ctx, request, &response);
       }});
}

void saveResource(Resource *resource, jint namespace_, jint type, jint name) {
  resource->set_type(type);
  resource->set_namespace_(namespace_);
  resource->set_name(name);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addString(
    JNIEnv *env, jclass clazz, jlong jevent, jint id, jstring str) {
  JStringWrapper str_wrapper(env, str);

  PropertyEvent *event = (PropertyEvent *)jevent;
  StringEntry *string_entry = event->add_string();
  string_entry->set_id(id);
  string_entry->set_str(str_wrapper.get().c_str());
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addPropertySource(
    JNIEnv *env, jclass clazz, jlong jproperty, jint namespace_, jint type,
    jint name) {
  Property *property = (Property *)jproperty;
  saveResource(property->mutable_source(), namespace_, type, name);
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addIntProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jint name, jint type, jint value) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  auto *property = event->add_property();
  property->set_name(name);
  property->set_type(static_cast<Property_Type>(type));
  property->set_int32_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addLongProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jint name, jint type,
    jlong value) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  auto *property = event->add_property();
  property->set_name(name);
  property->set_type(static_cast<Property_Type>(type));
  property->set_int64_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addDoubleProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jint name, jint type,
    jdouble value) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  auto *property = event->add_property();
  property->set_name(name);
  property->set_type(static_cast<Property_Type>(type));
  property->set_double_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addFloatProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jint name, jint type,
    jfloat value) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  auto *property = event->add_property();
  property->set_name(name);
  property->set_type(static_cast<Property_Type>(type));
  property->set_float_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addResourceProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jint name, jint type,
    jint resource_namespace, jint resource_type, jint resource_name) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  auto *property = event->add_property();
  property->set_name(name);
  property->set_type(static_cast<Property_Type>(type));
  saveResource(property->mutable_resource_value(), resource_namespace,
               resource_type, resource_name);
  return (long)property;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_addLayoutResource(
    JNIEnv *env, jclass clazz, jlong jevent, jint namespace_, jint type,
    jint name) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  saveResource(event->mutable_layout(), namespace_, type, name);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_LayoutInspectorService_sendProperties(
    JNIEnv *env, jclass clazz, jlong jevent, jlong viewId) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  event->set_view_id((long)viewId);
  PropertyEvent property_event = *event;

  // Note: property_event is copied by value here which is not optimal.
  Agent::Instance().SubmitAgentTasks(
      {[property_event](AgentService::Stub &stub,
                        grpc::ClientContext &ctx) mutable {
        SendEventRequest request;
        auto *event = request.mutable_event();
        auto *inspector_event = event->mutable_layout_inspector_event();
        auto *properties = inspector_event->mutable_properties();
        *properties = property_event;
        event->set_is_ended(true);
        event->set_kind(Event::LAYOUT_INSPECTOR);
        event->set_group_id(Event::PROPERTIES);
        EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}
}
