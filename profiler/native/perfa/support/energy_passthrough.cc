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
#include <string>

#include "perfa/perfa.h"
#include "perfa/support/jni_wrappers.h"
#include "utils/clock.h"
#include "utils/uid_fetcher.h"

using grpc::ClientContext;
using profiler::JStringWrapper;
using profiler::Perfa;
using profiler::SteadyClock;
using profiler::UidFetcher;
using profiler::proto::EmptyEnergyReply;
using profiler::proto::RecordWakeLockEventRequest;
using profiler::proto::WakeLockEvent;
using std::string;

namespace {

static constexpr const char* kWindowWakeLockName = "Window Wake Lock";

void SendEnergyStats(WakeLockEvent::WakeLockType type,
                     WakeLockEvent::WakeLockAction action, const string& name) {
  auto energy_service_stub = Perfa::Instance().energy_stub();

  SteadyClock clock;
  ClientContext context;
  RecordWakeLockEventRequest request;
  EmptyEnergyReply reply;

  // TODO Should we cache the UIDs? Re-getting the UID every time the user does
  // something wakelock related doesn't make sense if the UIDs are almost
  // always the same.
  request.set_app_id(UidFetcher::GetUid(getpid()));
  request.mutable_event()->set_timestamp(clock.GetCurrentTime());
  request.mutable_event()->set_type(type);
  request.mutable_event()->set_action(action);
  request.mutable_event()->set_name(name);

  energy_service_stub.RecordWakeLockEvent(&context, request, &reply);
}

}  // namespace profiler

extern "C" {

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WindowWakeLockTracker_onWindowWakeLockAcquired(
    JNIEnv* env, jclass clazz) {
  SendEnergyStats(WakeLockEvent::WINDOW, WakeLockEvent::ACQUIRED,
                  kWindowWakeLockName);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_WindowWakeLockTracker_onWindowWakeLockReleased(
    JNIEnv* env, jclass clazz) {
  SendEnergyStats(WakeLockEvent::WINDOW, WakeLockEvent::RELEASED_MANUAL,
                  kWindowWakeLockName);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_PowerManagerWakeLockTracker_onPowerManagerWakeLockCreated(
    JNIEnv* env, jclass clazz, jstring jtag) {
  JStringWrapper wrappedTag(env, jtag);
  SendEnergyStats(WakeLockEvent::PM, WakeLockEvent::CREATED, wrappedTag.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_PowerManagerWakeLockTracker_onPowerManagerWakeLockAcquired(
    JNIEnv* env, jclass clazz, jstring jtag, jlong jtimeout) {
  JStringWrapper wrappedTag(env, jtag);
  SendEnergyStats(WakeLockEvent::PM, WakeLockEvent::ACQUIRED, wrappedTag.get());
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_energy_PowerManagerWakeLockTracker_onPowerManagerWakeLockReleased(
    JNIEnv* env, jclass clazz, jstring jtag, jboolean jwas_auto_release) {
  JStringWrapper wrappedTag(env, jtag);
  if (((bool)jwas_auto_release)) {
    SendEnergyStats(WakeLockEvent::PM, WakeLockEvent::RELEASED_AUTOMATIC,
                    wrappedTag.get());
  } else {
    SendEnergyStats(WakeLockEvent::PM, WakeLockEvent::RELEASED_MANUAL,
                    wrappedTag.get());
  }
}
};
