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
#include <sstream>
#include "agent/agent.h"
#include "agent/jni_wrappers.h"

using layoutinspector::ComponentTreeEvent;
using layoutinspector::Resource;
using layoutinspector::StringEntry;
using layoutinspector::View;
using profiler::JStringWrapper;

/**
 * Native calls for loading the view hierarchy into an ComponentTreeEvent
 * protobuf.
 */
extern "C" {

extern void saveResource(Resource *resource, jint namespace_, jint type,
                         jint name);

/*
 * Select the View instance. Used by the JNI function addView.
 * The parameter jevent is either:
 *   - a ComponentTreeEvent pointer (for the root view)
 *   - a View pointer (for adding a sub view).
 *
 * This function interprets jevent and returns a View pointer for either:
 *   - the root view
 *   - a newly added sub view to the view indicated by jevent
 */
View *selectView(JNIEnv *env, jlong jevent, bool isSubView) {
  if (isSubView) {
    View *view = (View *)jevent;
    return view->add_sub_view();
  } else {
    ComponentTreeEvent *event = (ComponentTreeEvent *)jevent;
    return event->mutable_root();
  }
}

/*
 * Add a string to the string table in a ComponentTreeEvent.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ComponentTree_addString(
    JNIEnv *env, jclass clazz, jlong jevent, jint id, jstring str) {
  JStringWrapper str_wrapper(env, str);

  ComponentTreeEvent *event = (ComponentTreeEvent *)jevent;
  StringEntry *string_entry = event->add_string();
  string_entry->set_id(id);
  string_entry->set_str(str_wrapper.get().c_str());
}

/*
 * Add the root view or a sub view to a ComponentTreeEvent.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ComponentTree_addView(
    JNIEnv *env, jclass clazz, jlong jevent, bool isSubView, jlong drawId,
    jint x, jint y, jint width, jint height, jint className, jint packageName,
    jint textValue) {
  View *view = selectView(env, jevent, isSubView);
  view->set_draw_id(drawId);
  view->set_x(x);
  view->set_y(y);
  view->set_width(width);
  view->set_height(height);
  view->set_class_name(className);
  view->set_package_name(packageName);
  view->set_text_value(textValue);
}

/*
 * Add the View id as a resource to a View.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ComponentTree_addIdResource(
    JNIEnv *env, jclass clazz, jlong jview, jint namespace_, jint type,
    jint name) {
  View *view = (View *)jview;
  saveResource(view->mutable_view_id(), namespace_, type, name);
}

/*
 * Add the layout where the View was found as a resource to a View.
 */
JNIEXPORT void JNICALL
Java_com_android_tools_agent_layoutinspector_ComponentTree_addLayoutResource(
    JNIEnv *env, jclass clazz, jlong jview, jint namespace_, jint type,
    jint name) {
  View *view = (View *)jview;
  saveResource(view->mutable_layout(), namespace_, type, name);
}
}
