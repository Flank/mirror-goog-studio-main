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

#include "agent/agent.h"
#include "agent/support/jni_wrappers.h"

using grpc::ClientContext;
using profiler::Agent;
using profiler::SteadyClock;
using profiler::proto::AddEnergyEventRequest;
using profiler::proto::EmptyEnergyReply;
using profiler::proto::EnergyEvent;
using profiler::proto::InternalEnergyService;

namespace {

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
      {[&energy_event, timestamp](InternalEnergyService::Stub& stub,
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
    JNIEnv* env, jobject thiz) {
  EnergyEvent energy_event;
  // TODO: Set metadata here
  // energy_event.mutable_wake_lock_acquired()->...
  SubmitEnergyEvent(energy_event);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockReleased(
    JNIEnv* env, jobject thiz) {
  EnergyEvent energy_event;
  // TODO: Set metadata here
  // energy_event.mutable_wake_lock_released()->...
  SubmitEnergyEvent(energy_event);
}
};