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

using layoutinspector::View;

/**
 * Native calls for loading the view hierarchy into an ComponentTreeEvent
 * protobuf.
 */
extern "C" {

/*
 * Add a compose view to a parent View proto.
 */
JNIEXPORT jlong JNICALL
Java_com_android_tools_agent_layoutinspector_ComposeTree_addComposeView(
    JNIEnv *env, jclass clazz, jlong jparent, jlong drawId, jint x, jint y,
    jint width, jint height, jint className, jint filename, jint invocationName,
    jint invocationPackageName, jint lineNumber) {
  View *parent = (View *)jparent;
  View *view = parent->add_sub_view();
  view->set_draw_id(drawId);
  view->set_x(x);
  view->set_y(y);
  view->set_width(width);
  view->set_height(height);
  view->set_class_name(className);
  view->set_compose_filename(filename);
  view->set_compose_invocation(invocationName);
  view->set_compose_package(invocationPackageName);
  view->set_compose_line_number(lineNumber);
  return (long)view;
}
}
