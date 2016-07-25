/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "perfa/perfa.h"
#include "perfa/support/jni_wrappers.h"
#include "utils/clock.h"

using grpc::ClientContext;
using profiler::SteadyClock;
using profiler::Perfa;
using profiler::proto::ActivityEventData;
using profiler::proto::FragmentEventData;
using profiler::proto::EventProfilerData;
using profiler::proto::SystemEventData;
using profiler::proto::ProfilerData;
using profiler::proto::EmptyEventResponse;
using profiler::JStringWrapper;

namespace {

void SendData(EventProfilerData* data) {
  auto event_stub = Perfa::Instance().event_stub();
  // TODO: Use a consistent clock for all RPC calls. In the future the clock
  // should come from
  // Perfa, or a pre-populated ProfilerData should be requested from Perfa.
  SteadyClock clock;
  ClientContext context;
  EmptyEventResponse response;

  ProfilerData* profiler_data = data->mutable_basic_info();
  profiler_data->set_end_timestamp(clock.GetCurrentTime());
  event_stub.SendEvent(&context, *data, &response);
  // TODO: Handle response codes.
}

void SendSystemEvent(const SystemEventData& event) {
  EventProfilerData data;
  data.mutable_system_data()->CopyFrom(event);
  SendData(&data);
}

// TODO: Combine activity and fragment protos, fragments are a subset of
// activity.
void SendActivityEvent(JNIEnv* env, const jstring& name,
                       const ActivityEventData::ActivityState& state,
                       int hash) {
  JStringWrapper activity_name(env, name);
  EventProfilerData data;
  ActivityEventData* activity = data.mutable_activity_data();
  activity->set_name(activity_name.get());
  activity->set_activity_state(state);
  activity->set_activity_hash(hash);
  SendData(&data);
}

void SendFragmentEvent(JNIEnv* env, const jstring& name,
                       const FragmentEventData::FragmentState& state,
                       int hash) {
  JStringWrapper fragment_name(env, name);
  EventProfilerData data;
  FragmentEventData* fragment = data.mutable_fragment_data();
  fragment->set_name(fragment_name.get());
  fragment->set_fragment_state(state);
  fragment->set_fragment_hash(hash);
  SendData(&data);
}
}

extern "C" {
// TODO: Create figure out how to autogenerate this class, to avoid typo errors.

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_WindowProfilerCallback_sendTouchEvent(
    JNIEnv* env, jobject thiz, jint jstate) {
  SystemEventData event;
  event.set_type(SystemEventData::TOUCH);
  event.set_action_id((int)jstate);
  SendSystemEvent(event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_WindowProfilerCallback_sendKeyEvent(
    JNIEnv* env, jobject thiz, jint jstate) {
  SystemEventData event;
  event.set_type(SystemEventData::KEY);
  event.set_action_id((int)jstate);
  SendSystemEvent(event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityCreated(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::CREATED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityStarted(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::STARTED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityResumed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::RESUMED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityPaused(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::PAUSED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityStopped(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::STOPPED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityDestroyed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::DESTROYED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivitySaved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendActivityEvent(env, jname, ActivityEventData::SAVED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendFragmentAdded(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendFragmentEvent(env, jname, FragmentEventData::ADDED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendFragmentRemoved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  SendFragmentEvent(env, jname, FragmentEventData::REMOVED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentAttached(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::ATTACHED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentCreated(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::CREATED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentCreatedView(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::CREATEDVIEW, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentActivityCreated(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::ACTIVITYCREATED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentStarted(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::STARTED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentResumed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::RESUMED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentPaused(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::PAUSED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentStopped(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::STOPPED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentDestroyedView(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::DESTROYEDVIEW, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentDestroyed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::DESTROYED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentDetached(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
   SendFragmentEvent(env, jname, FragmentEventData::DETACHED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendRotationEvent(
    JNIEnv* env, jobject thiz, jint jstate) {
  SystemEventData event;
  event.set_type(SystemEventData::ROTATION);
  event.set_action_id((int)jstate);
  SendSystemEvent(event);
}
};
