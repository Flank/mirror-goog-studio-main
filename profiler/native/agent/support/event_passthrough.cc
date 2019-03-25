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
#include "agent/jni_wrappers.h"
#include "event_manager.h"
#include "utils/clock.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::EventManager;
using profiler::SteadyClock;
using profiler::proto::ActivityStateData;
using profiler::proto::AgentService;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::InteractionData;
using profiler::proto::InternalEventService;
using profiler::proto::SendActivityDataRequest;
using profiler::proto::SendEventRequest;
using profiler::proto::SendSystemDataRequest;
using profiler::proto::SystemData;
using profiler::proto::ViewData;

using profiler::JStringWrapper;
using profiler::proto::EmptyEventResponse;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

Status SendViewEvent(AgentService::Stub& stub, ClientContext& ctx,
                     ViewData* data, int32_t pid, int64_t timestamp,
                     int64_t view_id, bool is_end) {
  SendEventRequest request;
  auto* event = request.mutable_event();
  event->set_pid(pid);
  event->set_group_id(view_id);
  event->set_is_ended(is_end);
  event->set_kind(Event::VIEW);
  event->set_timestamp(timestamp);
  event->mutable_view()->CopyFrom(*data);

  EmptyResponse response;
  return stub.SendEvent(&ctx, request, &response);
}

Status SendSystemEvent(AgentService::Stub& stub, ClientContext& ctx,
                       InteractionData* data, int32_t pid, int64_t timestamp,
                       long downtime, bool is_end) {
  SendEventRequest request;
  auto* event = request.mutable_event();
  event->set_pid(pid);
  event->set_group_id(downtime);
  event->set_is_ended(is_end);
  event->set_kind(Event::INTERACTION);
  event->set_timestamp(timestamp);
  event->mutable_interaction()->CopyFrom(*data);

  EmptyResponse response;
  return stub.SendEvent(&ctx, request, &response);
}

Status SendLegacySystemEvent(InternalEventService::Stub& stub,
                             ClientContext& ctx, SystemData* event, int32_t pid,
                             int64_t timestamp, long jdownTime) {
  event->set_start_timestamp(timestamp);
  event->set_end_timestamp(0);
  event->set_event_id(jdownTime);

  SendSystemDataRequest request;
  request.set_pid(pid);
  request.mutable_data()->CopyFrom(*event);
  EmptyEventResponse response;
  return stub.SendSystem(&ctx, request, &response);
}

void SendKeyboardEvent(JStringWrapper& text, int64_t event_down_time) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();

  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[pid, text, timestamp, event_down_time](AgentService::Stub& stub,
                                                 ClientContext& ctx) mutable {
          InteractionData data;
          data.set_type(InteractionData::KEY);
          data.set_event_data(text.get());
          return SendSystemEvent(stub, ctx, &data, pid, timestamp,
                                 event_down_time, true);
        }});
  } else {
    Agent::Instance().SubmitEventTasks(
        {[pid, text, timestamp, event_down_time](
             InternalEventService::Stub& stub, ClientContext& ctx) {
          SystemData event;
          event.set_type(InteractionData::KEY);
          event.set_event_data(text.get());
          return SendLegacySystemEvent(stub, ctx, &event, pid, timestamp,
                                       event_down_time);
        }});
  }
}

void EnqueueActivityEvent(JNIEnv* env, const jstring& name,
                          const ViewData::State& state, int hash,
                          int parent_activity_hash) {
  JStringWrapper activity_name(env, name);
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    bool is_end_state;
    switch (state) {
      case ViewData::PAUSED:
      case ViewData::STOPPED:
      case ViewData::DESTROYED:
      case ViewData::SAVED:
      case ViewData::REMOVED:
        is_end_state = true;
        break;
      default:
        is_end_state = false;
        break;
    }

    Agent::Instance().SubmitAgentTasks(
        {[pid, timestamp, activity_name, state, hash, parent_activity_hash,
          is_end_state](AgentService::Stub& stub, ClientContext& ctx) mutable {
          ViewData data;
          data.set_name(activity_name.get());
          data.set_state(state);
          if (parent_activity_hash != 0) {
            data.set_parent_activity_id(parent_activity_hash ^ pid);
          }
          return SendViewEvent(stub, ctx, &data, pid, timestamp, (hash ^ pid),
                               is_end_state);
        }});
  } else {
    SendActivityDataRequest request;
    request.set_pid(pid);

    auto* data = request.mutable_data();
    data->set_name(activity_name.get());
    data->set_hash(hash ^ pid);
    if (parent_activity_hash != 0) {
      data->set_activity_context_hash(parent_activity_hash ^ pid);
    }

    ActivityStateData* state_data = data->add_state_changes();
    state_data->set_state(state);
    state_data->set_timestamp(timestamp);
    EventManager::Instance().CacheAndEnqueueActivityEvent(request);
  }
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
    JNIEnv* env, jobject thiz, jint jstate, jlong jdownTime,
    jboolean jis_up_event) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();

  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[jdownTime, jstate, pid, timestamp, jis_up_event](
             AgentService::Stub& stub, ClientContext& ctx) mutable {
          InteractionData data;
          data.set_type(InteractionData::TOUCH);
          data.set_action_id(jstate);
          return SendSystemEvent(stub, ctx, &data, pid, timestamp, jdownTime,
                                 jis_up_event);
        }});
  } else {
    Agent::Instance().SubmitEventTasks(
        {[jdownTime, jstate, pid, timestamp](InternalEventService::Stub& stub,
                                             ClientContext& ctx) {
          SystemData event;
          event.set_type(InteractionData::TOUCH);
          event.set_action_id(jstate);
          return SendLegacySystemEvent(stub, ctx, &event, pid, timestamp,
                                       jdownTime);
        }});
  }
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
  EnqueueActivityEvent(env, jname, ViewData::CREATED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityStarted(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ViewData::STARTED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityResumed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ViewData::RESUMED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityPaused(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ViewData::PAUSED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityStopped(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ViewData::STOPPED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivityDestroyed(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ViewData::DESTROYED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendActivitySaved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash) {
  EnqueueActivityEvent(env, jname, ViewData::SAVED, jhash, 0);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_FragmentWrapper_sendFragmentAdded(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash, jint activity_hash) {
  EnqueueActivityEvent(env, jname, ViewData::ADDED, jhash, activity_hash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_event_FragmentWrapper_sendFragmentRemoved(
    JNIEnv* env, jobject thiz, jstring jname, jint jhash, jint activity_hash) {
  EnqueueActivityEvent(env, jname, ViewData::REMOVED, jhash, activity_hash);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_profilers_EventProfiler_sendRotationEvent(
    JNIEnv* env, jobject thiz, jint jstate) {
  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();

  if (Agent::Instance().agent_config().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks({[jstate, pid, timestamp](
                                            AgentService::Stub& stub,
                                            ClientContext& ctx) mutable {
      InteractionData data;
      data.set_type(InteractionData::ROTATION);
      data.set_action_id(jstate);
      return SendSystemEvent(stub, ctx, &data, pid, timestamp, timestamp, true);
    }});
  } else {
    Agent::Instance().SubmitEventTasks(
        {[jstate, pid, timestamp](InternalEventService::Stub& stub,
                                  ClientContext& ctx) {
          SystemData event;
          event.set_type(InteractionData::ROTATION);
          event.set_action_id(jstate);
          // Give rotation events a unique id.
          return SendLegacySystemEvent(stub, ctx, &event, pid, timestamp,
                                       timestamp);
        }});
  }
}
};
