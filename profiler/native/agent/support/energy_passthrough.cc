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
using grpc::Status;
using profiler::Agent;
using profiler::SteadyClock;
using profiler::proto::EmptyEnergyReply;
using profiler::proto::InternalEnergyService;
using profiler::proto::WakeLockEventRequest;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

void EnqueueWakeLockEvent() {
  int64_t timestamp = GetClock().GetCurrentTime();
  Agent::Instance().SubmitEnergyTasks({[timestamp](
      InternalEnergyService::Stub& stub, ClientContext& ctx) {
    WakeLockEventRequest request;
    request.set_timestamp(timestamp);
    EmptyEnergyReply response;
    return stub.SendWakeLockEvent(&ctx, request, &response);
  }});
}
}  // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockAcquired(
    JNIEnv* env, jobject thiz) {
  // TODO: Pass wake lock info.
  EnqueueWakeLockEvent();
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WakeLockWrapper_sendWakeLockReleased(
    JNIEnv* env, jobject thiz) {
  // TODO: Pass wake lock info.
  EnqueueWakeLockEvent();
}
};