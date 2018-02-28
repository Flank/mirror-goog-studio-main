/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"

using grpc::ClientContext;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::SteadyClock;
using profiler::proto::AddEnergyEventRequest;
using profiler::proto::AlarmSet;
using profiler::proto::EmptyEnergyReply;
using profiler::proto::EnergyEvent;
using profiler::proto::InternalEnergyService;
using profiler::proto::WakeLockAcquired;
using profiler::proto::WakeLockReleased;

namespace {

// In order to parse wake lock flags we fork constant values from
// https://developer.android.com/reference/android/os/PowerManager.html

// Wake lock levels
constexpr int WAKE_LOCK_LEVEL_MASK = 0x0000ffff;
constexpr int PARTIAL_WAKE_LOCK = 0x00000001;
constexpr int SCREEN_DIM_WAKE_LOCK = 0x00000006;
constexpr int SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a;
constexpr int FULL_WAKE_LOCK = 0x0000001a;
constexpr int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 0x00000020;

// Wake lock flags
constexpr int ACQUIRE_CAUSES_WAKEUP = 0x10000000;
constexpr int ON_AFTER_RELEASE = 0x20000000;

// Wake lock release flags
constexpr int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 0x00000001;

// In order to parse alarm types we fork constant values from
// https://developer.android.com/reference/android/app/AlarmManager.html

// Alarm types
constexpr int RTC = 0x00000001;
constexpr int RTC_WAKEUP = 0x00000000;
constexpr int ELAPSED_REALTIME = 0x00000003;
constexpr int ELAPSED_REALTIME_WAKEUP = 0x00000002;

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

// Enqueue and submit the target |energy_event|. The event's timestamp will be
// set as a side-effect of calling this method, but all other fields and
// appropriate metadata must be set by the caller.
void SubmitEnergyEvent(const EnergyEvent& energy_event,
                       const std::string& stack = {}) {
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitEnergyTasks(
      {[energy_event, stack, timestamp](InternalEnergyService::Stub& stub,
                                        ClientContext& ctx) {
        AddEnergyEventRequest request;
        request.mutable_energy_event()->CopyFrom(energy_event);
        request.mutable_energy_event()->set_timestamp(timestamp);
        request.set_callstack(stack);

        EmptyEnergyReply response;
        return stub.AddEnergyEvent(&ctx, request, &response);
      }});
}

AlarmSet::Type ParseAlarmType(jint type) {
  switch (type) {
    case RTC:
      return AlarmSet::RTC;
    case RTC_WAKEUP:
      return AlarmSet::RTC_WAKEUP;
    case ELAPSED_REALTIME:
      return AlarmSet::ELAPSED_REALTIME;
    case ELAPSED_REALTIME_WAKEUP:
      return AlarmSet::ELAPSED_REALTIME_WAKEUP;
    default:
      return AlarmSet::UNDEFINED_ALARM_TYPE;
  }
}
}  // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockAcquired(
    JNIEnv* env, jclass clazz, jint event_id, jint flags, jstring tag,
    jlong timeout, jstring stack) {
  JStringWrapper tag_string(env, tag);
  JStringWrapper stack_string(env, stack);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto wake_lock_acquired = energy_event.mutable_wake_lock_acquired();
  WakeLockAcquired::Level level;
  switch (flags & WAKE_LOCK_LEVEL_MASK) {
    case PARTIAL_WAKE_LOCK:
      level = WakeLockAcquired::PARTIAL_WAKE_LOCK;
      break;
    case SCREEN_DIM_WAKE_LOCK:
      level = WakeLockAcquired::SCREEN_DIM_WAKE_LOCK;
      break;
    case SCREEN_BRIGHT_WAKE_LOCK:
      level = WakeLockAcquired::SCREEN_BRIGHT_WAKE_LOCK;
      break;
    case FULL_WAKE_LOCK:
      level = WakeLockAcquired::FULL_WAKE_LOCK;
      break;
    case PROXIMITY_SCREEN_OFF_WAKE_LOCK:
      level = WakeLockAcquired::PROXIMITY_SCREEN_OFF_WAKE_LOCK;
      break;
    default:
      level = WakeLockAcquired::UNDEFINED_WAKE_LOCK_LEVEL;
      break;
  }
  wake_lock_acquired->set_level(level);
  if ((flags & ACQUIRE_CAUSES_WAKEUP) != 0) {
    wake_lock_acquired->mutable_flags()->Add(
        WakeLockAcquired::ACQUIRE_CAUSES_WAKEUP);
  }
  if ((flags & ON_AFTER_RELEASE) != 0) {
    wake_lock_acquired->mutable_flags()->Add(
        WakeLockAcquired::ON_AFTER_RELEASE);
  }
  wake_lock_acquired->set_tag(tag_string.get());
  wake_lock_acquired->set_timeout(timeout);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockReleased(
    JNIEnv* env, jclass clazz, jint event_id, jint flags, jboolean is_held,
    jstring stack) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  if ((flags & RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) != 0) {
    energy_event.mutable_wake_lock_released()->mutable_flags()->Add(
        WakeLockReleased::RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
  }
  energy_event.mutable_wake_lock_released()->set_is_held(is_held);
  JStringWrapper stack_string(env, stack);
  SubmitEnergyEvent(energy_event, stack_string.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendIntentAlarmScheduled(
    JNIEnv* env, jclass clazz, jint event_id, jint type, jlong trigger_ms,
    jlong window_ms, jlong interval_ms, jstring creator_package,
    jint creator_uid) {
  JStringWrapper creator_package_str(env, creator_package);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto alarm_set = energy_event.mutable_alarm_set();
  alarm_set->set_type(ParseAlarmType(type));
  alarm_set->set_trigger_ms(trigger_ms);
  alarm_set->set_window_ms(window_ms);
  alarm_set->set_interval_ms(interval_ms);
  alarm_set->mutable_operation()->set_creator_package(
      creator_package_str.get());
  alarm_set->mutable_operation()->set_creator_uid(creator_uid);
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendListenerAlarmScheduled(
    JNIEnv* env, jclass clazz, jint event_id, jint type, jlong trigger_ms,
    jlong window_ms, jlong interval_ms, jstring listener_tag) {
  JStringWrapper listener_tag_str(env, listener_tag);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto alarm_set = energy_event.mutable_alarm_set();
  alarm_set->set_type(ParseAlarmType(type));
  alarm_set->set_trigger_ms(trigger_ms);
  alarm_set->set_window_ms(window_ms);
  alarm_set->set_interval_ms(interval_ms);
  alarm_set->mutable_listener()->set_tag(listener_tag_str.get());
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendIntentAlarmCancelled(
    JNIEnv* env, jclass clazz, jint event_id, jstring creator_package,
    jint creator_uid) {
  JStringWrapper creator_package_str(env, creator_package);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  auto alarm_cancelled = energy_event.mutable_alarm_cancelled();
  alarm_cancelled->mutable_operation()->set_creator_package(
      creator_package_str.get());
  alarm_cancelled->mutable_operation()->set_creator_uid(creator_uid);
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_AlarmManagerWrapper_sendListenerAlarmCancelled(
    JNIEnv* env, jclass clazz, jint event_id, jstring listener_tag) {
  JStringWrapper listener_tag_str(env, listener_tag);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(event_id);
  energy_event.mutable_alarm_cancelled()->mutable_listener()->set_tag(
      listener_tag_str.get());
  SubmitEnergyEvent(energy_event);
}
};