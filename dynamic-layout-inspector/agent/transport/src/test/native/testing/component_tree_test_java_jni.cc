/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include <string>
#include "proto/layout_inspector.grpc.pb.h"

using layoutinspector::ComponentTreeEvent;

/**
 * Native calls for loading the properties event protobuf.
 */
extern "C" {

JNIEXPORT long JNICALL
Java_com_android_tools_agent_layoutinspector_ComponentTreeTest_allocateEvent(
    JNIEnv *env, jclass clazz) {
  ComponentTreeEvent *request = new ComponentTreeEvent();
  return (long)request;
}

JNIEXPORT jbyteArray JNICALL
Java_com_android_tools_agent_layoutinspector_ComponentTreeTest_toByteArray(
    JNIEnv *env, jclass clazz, jlong event) {
  ComponentTreeEvent *request = (ComponentTreeEvent *)event;
  std::string str;
  request->SerializeToString(&str);
  delete request;

  int size = str.length();
  jbyteArray result = env->NewByteArray(size);
  env->SetByteArrayRegion(result, 0, size, (jbyte *)(str.c_str()));
  return result;
}
}
