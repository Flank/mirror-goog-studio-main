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

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"
#include "event_manager.h"
#include "utils/clock.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::EventManager;
using profiler::SteadyClock;
using profiler::proto::ActivityData;
using profiler::proto::ActivityStateData;
using profiler::proto::FragmentData;
using profiler::proto::InternalEventService;
using profiler::proto::SystemData;

using profiler::JStringWrapper;
using profiler::proto::EmptyEventResponse;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

Status SendSystemEvent(InternalEventService::Stub& stub, ClientContext& ctx,
                       SystemData* event, int32_t pid, int64_t timestamp,
                       long jdownTime) {
  event->set_start_timestamp(timestamp);
  event->set_end_timestamp(0);
  event->set_pid(pid);
  event->set_event_id(jdownTime);

  EmptyEventResponse response;
  return stub.SendSystem(&ctx, *event, &response);
}

void SendKeyboardEvent(JStringWrapper& text, int64_t event_down_time) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    return;
  }

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();

  Agent::Instance().SubmitEventTasks({[pid, text, timestamp, event_down_time](
                                          InternalEventService::Stub& stub,
                                          ClientContext& ctx) {
    SystemData event;
    event.set_type(SystemData::KEY);
    event.set_event_data(text.get());
    return SendSystemEvent(stub, ctx, &event, pid, timestamp, event_down_time);
  }});
}

void EnqueueActivityDataEvent(JNIEnv* env, const jstring& name,
                              const ActivityStateData::ActivityState& state,
                              int hash, FragmentData* fragment) {
  JStringWrapper activity_name(env, name);
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  ActivityData activity;
  activity.set_name(activity_name.get());
  activity.set_pid(pid);
  activity.set_hash(hash ^ pid);
  if (fragment != nullptr) {
    activity.mutable_fragment_data()->set_activity_context_hash(
        fragment->activity_context_hash() ^ pid);
  }
  ActivityStateData* state_data = activity.add_state_changes();
  state_data->set_state(state);
  state_data->set_timestamp(timestamp);
  EventManager::Instance().CacheAndEnqueueActivityEvent(activity);
}

void EnqueueActivityEvent(JNIEnv* env, const jstring& name,
                          const ActivityStateData::ActivityState& state,
                          int hash) {
  EnqueueActivityDataEvent(env, name, state, hash, nullptr);
}

void EnqueueFragmentEvent(JNIEnv* env, const jstring& name,
                          const ActivityStateData::ActivityState& state,
                          int hash, int activityContextHash) {
  FragmentData fragment;
  fragment.set_activity_context_hash(activityContextHash);
  EnqueueActivityDataEvent(env, name, state, hash, &fragment);
}
}  // namespace

extern "C" {
// TODO: Create figure out how to autogenerate this class, to avoid typo errors.
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_InputConnectionWrapper_sendKeyboardEvent(
    JNIEnv* env, jobject thiz, jstring jtext) {
  JStringWrapper text(env, jtext);
  int64_t timestamp = GetClock().GetCurrentTime();
  SendKeyboardEvent(text, timestamp);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_WindowProfilerCallback_sendTouchEvent(
    JNIEnv* env, jobject thiz, jint jstate, jlong jdownTime) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    return;
  }

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();

  Agent::Instance().SubmitEventTasks(
      {[jdownTime, jstate, pid, timestamp](InternalEventService::Stub& stub,
                                           ClientContext& ctx) {
        SystemData event;
        event.set_type(SystemData::TOUCH);
        event.set_action_id(jstate);
        return SendSystemEvent(stub, ctx, &event, pid, timestamp, jdownTime);
      }});
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_WindowProfilerCallback_sendKeyEvent(
    JNIEnv* env, jobject thiz, jstring jtext, jlong jdownTime) {
  JStringWrapper text(env, jtext);
  SendKeyboardEvent(text, jdownTime);
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
Java_com_android_tools_profiler_support_event_FragmentWrapper_sendFragmentAdded(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash, jint activity_hash) {
  EnqueueFragmentEvent(env, jname, ActivityStateData::ADDED, jhash,
                       activity_hash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_FragmentWrapper_sendFragmentRemoved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash, jint activity_hash) {
  EnqueueFragmentEvent(env, jname, ActivityStateData::REMOVED, jhash,
                       activity_hash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendRotationEvent(
    JNIEnv* env, jobject thiz, jint jstate) {
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    return;
  }

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();

  Agent::Instance().SubmitEventTasks(
      {[jstate, pid, timestamp](InternalEventService::Stub& stub,
                                ClientContext& ctx) {
        SystemData event;
        event.set_type(SystemData::ROTATION);
        event.set_action_id(jstate);
        // Give rotation events a unique id.
        return SendSystemEvent(stub, ctx, &event, pid, timestamp, timestamp);
      }});
}
};
