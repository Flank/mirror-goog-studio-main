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
#include "utils/clock.h"

using grpc::ClientContext;
using profiler::SteadyClock;
using profiler::Perfa;
using profiler::proto::ActivityEventData;
using profiler::proto::EventProfilerData;
using profiler::proto::SystemEventData;
using profiler::proto::ProfilerData;
using profiler::proto::EmptyEventResponse;

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

void SendActivityEvent(JNIEnv* env, const jstring& name,
                       const ActivityEventData::ActivityState& state,
                       int hash) {
  const char* nativeString = env->GetStringUTFChars(name, 0);
  EventProfilerData data;
  ActivityEventData* activity = data.mutable_activity_data();
  activity->set_name(nativeString);
  activity->set_activity_state(state);
  activity->set_activity_hash(hash);
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
};
