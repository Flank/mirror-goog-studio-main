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
#include <unistd.h>

#include "perfa/perfa.h"
#include "perfa/support/jni_wrappers.h"
#include "utils/clock.h"

using grpc::ClientContext;
using profiler::SteadyClock;
using profiler::Perfa;
using profiler::proto::ActivityData;
using profiler::proto::ActivityStateData;
using profiler::proto::SystemData;
using profiler::proto::FragmentEventData;

using profiler::proto::EmptyEventResponse;
using profiler::JStringWrapper;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

void SendSystemEvent(SystemData* event, int32_t pid, int64_t timestamp,
                     long jdownTime) {
  event->set_start_timestamp(timestamp);
  event->set_end_timestamp(0);
  event->set_process_id(pid);
  event->set_event_id(jdownTime);

  auto event_stub = Perfa::Instance().event_stub();
  ClientContext context;
  EmptyEventResponse response;
  event_stub.SendSystem(&context, *event, &response);
}

// TODO: Combine activity and fragment protos, fragments are a subset of
// activity.
void EnqueueActivityEvent(JNIEnv* env, const jstring& name,
                          const ActivityStateData::ActivityState& state,
                          int hash) {
  JStringWrapper activity_name(env, name);
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Perfa::Instance().background_queue()->EnqueueTask(
      [activity_name, hash, pid, state, timestamp]() {
        ActivityData activity;
        activity.set_name(activity_name.get());
        activity.set_process_id(pid);
        activity.set_hash(hash ^ pid);
        ActivityStateData* state_data = activity.add_state_changes();
        state_data->set_state(state);
        state_data->set_timestamp(timestamp);

        auto event_stub = Perfa::Instance().event_stub();
        ClientContext context;
        EmptyEventResponse response;
        event_stub.SendActivity(&context, activity, &response);
      });
}

void EnqueueFragmentEvent(JNIEnv* env, const jstring& name,
                          const FragmentEventData::FragmentState& state,
                          int hash) {
  // TODO using Perfa::Instance().background_queue()
}
}  // namespace

extern "C" {
// TODO: Create figure out how to autogenerate this class, to avoid typo errors.
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_InputConnectionWrapper_sendKeyboardEvent(
    JNIEnv* env, jobject thiz, jstring jtext) {
  JStringWrapper text(env, jtext);

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Perfa::Instance().background_queue()->EnqueueTask([pid, text, timestamp]() {
    SystemData event;
    event.set_type(SystemData::KEY);
    event.set_event_data(text.get());
    SendSystemEvent(&event, pid, timestamp, timestamp);
  });
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_WindowProfilerCallback_sendTouchEvent(
    JNIEnv* env, jobject thiz, jint jstate, jlong jdownTime) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Perfa::Instance().background_queue()->EnqueueTask(
      [jdownTime, jstate, pid, timestamp]() {
        SystemData event;
        event.set_type(SystemData::TOUCH);
        event.set_action_id(jstate);
        SendSystemEvent(&event, pid, timestamp, jdownTime);
      });
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_WindowProfilerCallback_sendKeyEvent(
    JNIEnv* env, jobject thiz, jint jstate, jlong jdownTime) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Perfa::Instance().background_queue()->EnqueueTask(
      [jdownTime, jstate, pid, timestamp]() {
        SystemData event;
        event.set_type(SystemData::KEY);
        event.set_action_id(jstate);
        SendSystemEvent(&event, pid, timestamp, jdownTime);
      });
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityCreated(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::CREATED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityStarted(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::STARTED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityResumed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::RESUMED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityPaused(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::PAUSED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityStopped(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::STOPPED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityDestroyed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::DESTROYED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivitySaved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::SAVED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_activity_ActivityWrapper_sendActivityOnRestart(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ActivityStateData::RESTARTED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendFragmentAdded(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::ADDED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendFragmentRemoved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::REMOVED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnAttach(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::ATTACHED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnCreate(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::CREATED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnCreateView(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::CREATEDVIEW, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnActivityCreated(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::ACTIVITYCREATED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnStart(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::STARTED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnResume(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::RESUMED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnPause(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::PAUSED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnStop(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::STOPPED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnDestroyView(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::DESTROYEDVIEW, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnDestroy(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::DESTROYED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_fragment_FragmentWrapper_sendFragmentOnDetach(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueFragmentEvent(env, jname, FragmentEventData::DETACHED, jhash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendRotationEvent(
    JNIEnv* env, jobject thiz, jint jstate) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Perfa::Instance().background_queue()->EnqueueTask([jstate, pid, timestamp]() {
    SystemData event;
    event.set_type(SystemData::ROTATION);
    event.set_action_id(jstate);
    // Give rotation events a unique id.
    SendSystemEvent(&event, pid, timestamp, timestamp);
  });
}
};
