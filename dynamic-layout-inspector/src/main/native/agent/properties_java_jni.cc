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
 * Native calls for loading the properties event protobuf.
 */
extern "C" {

void saveResource(Resource *resource, jint namespace_, jint type, jint name) {
  resource->set_type(type);
  resource->set_namespace_(namespace_);
  resource->set_name(name);
}

Property *addProperty(jlong jevent, jlong jproperty) {
  if (jproperty != 0L) {
    Property *property = (Property *)jproperty;
    return property->add_element();
  } else {
    PropertyEvent *event = (PropertyEvent *)jevent;
    return event->add_property();
  }
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_allocatePropertyEvent(
    JNIEnv *env, jclass clazz) {
  PropertyEvent *event = new PropertyEvent();
  return (long)event;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_freePropertyEvent(
    JNIEnv *env, jclass clazz, jlong jevent) {
  if (jevent != 0L) {
    delete (PropertyEvent *)jevent;
  }
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_sendPropertyEvent(
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
        event->set_pid(getpid());
        event->set_kind(Event::LAYOUT_INSPECTOR);
        event->set_group_id(Event::PROPERTIES);
        EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addString(
    JNIEnv *env, jclass clazz, jlong jevent, jint id, jstring str) {
  JStringWrapper str_wrapper(env, str);

  PropertyEvent *event = (PropertyEvent *)jevent;
  StringEntry *string_entry = event->add_string();
  string_entry->set_id(id);
  string_entry->set_str(str_wrapper.get().c_str());
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addPropertySource(
    JNIEnv *env, jclass clazz, jlong jproperty, jint namespace_, jint type,
    jint name) {
  Property *property = (Property *)jproperty;
  saveResource(property->mutable_source(), namespace_, type, name);
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addResolution(
    JNIEnv *env, jclass clazz, jlong jproperty, jint namespace_, jint type,
    jint name) {
  Property *property = (Property *)jproperty;
  saveResource(property->add_resolution_stack(), namespace_, type, name);
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addIntProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jint value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_int32_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addLongProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jlong value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_int64_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addDoubleProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jdouble value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_double_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addFloatProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jfloat value) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  property->set_float_value(value);
  return (long)property;
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addResourceProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type, jint resource_namespace, jint resource_type,
    jint resource_name) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  saveResource(property->mutable_resource_value(), resource_namespace,
               resource_type, resource_name);
  return (long)property;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addLayoutResource(
    JNIEnv *env, jclass clazz, jlong jevent, jint namespace_, jint type,
    jint name) {
  PropertyEvent *event = (PropertyEvent *)jevent;
  saveResource(event->mutable_layout(), namespace_, type, name);
}

JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addFlagProperty(
    JNIEnv *env, jclass clazz, jlong jevent, jlong jproperty, jint name,
    jboolean is_layout, jint type) {
  auto *property = addProperty(jevent, jproperty);
  property->set_name(name);
  property->set_is_layout(is_layout);
  property->set_type(static_cast<Property_Type>(type));
  return (long)property;
}

JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_Properties_addFlagPropertyValue(
    JNIEnv *env, jclass clazz, jlong jproperty, jint flag) {
  Property *property = (Property *)jproperty;
  auto *flags = property->mutable_flag_value();
  flags->add_flag(flag);
}
}
