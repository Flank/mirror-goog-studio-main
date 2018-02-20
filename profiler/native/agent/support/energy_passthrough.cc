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

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

// Enqueue and submit the target |energy_event|. The event's timestamp will be
// set as a side-effect of calling this method, but all other fields and
// appropriate metadata must be set by the caller.
void SubmitEnergyEvent(const EnergyEvent& energy_event) {
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitEnergyTasks(
      {[energy_event, timestamp](InternalEnergyService::Stub& stub,
                                 ClientContext& ctx) {
        AddEnergyEventRequest request;
        request.mutable_energy_event()->CopyFrom(energy_event);
        request.mutable_energy_event()->set_timestamp(timestamp);

        EmptyEnergyReply response;
        return stub.AddEnergyEvent(&ctx, request, &response);
      }});
}
}  // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockAcquired(
    JNIEnv* env, jclass clazz, jint wake_lock_id, jint flags, jstring tag,
    jlong timeout) {
  JStringWrapper tag_string(env, tag);
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(wake_lock_id);
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
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockReleased(
    JNIEnv* env, jclass clazz, jint wake_lock_id, jint flags,
    jboolean is_held) {
  EnergyEvent energy_event;
  energy_event.set_pid(getpid());
  energy_event.set_event_id(wake_lock_id);
  if ((flags & RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) != 0) {
    energy_event.mutable_wake_lock_released()->mutable_flags()->Add(
        WakeLockReleased::RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
  }
  energy_event.mutable_wake_lock_released()->set_is_held(is_held);
  SubmitEnergyEvent(energy_event);
}
};