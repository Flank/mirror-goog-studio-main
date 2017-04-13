/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "memory_agent.h"

#include <cstring>

#include "agent/support/memory_stats_logger.h"
#include "perfa/jvmti_helper.h"

namespace {
static JavaVM* vm_;
static profiler::MemoryAgent* agent_;
}

namespace profiler {

MemoryAgent* MemoryAgent::Instance(JavaVM* vm) {
  if (agent_ == nullptr) {
    // Create a stand-alone jvmtiEnv to avoid any callback conflicts
    // with other profilers' agents.
    vm_ = vm;
    jvmtiEnv* jvmti = CreateJvmtiEnv(vm_);
    agent_ = new MemoryAgent(jvmti);
    agent_->Initialize();
  }

  return agent_;
}

MemoryAgent::MemoryAgent(jvmtiEnv* jvmti) : jvmti_(jvmti) {}

void MemoryAgent::Initialize() {
  jvmtiError error;

  SetAllCapabilities(jvmti_);

  // Hook up event callbacks
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.GarbageCollectionStart = &GCStartCallback;
  callbacks.GarbageCollectionFinish = &GCFinishCallback;
  error = jvmti_->SetEventCallbacks(&callbacks, sizeof(callbacks));
  CheckJvmtiError(jvmti_, error);

  // Enable events
  SetEventNotification(jvmti_, JVMTI_ENABLE,
                       JVMTI_EVENT_GARBAGE_COLLECTION_START);
  SetEventNotification(jvmti_, JVMTI_ENABLE,
                       JVMTI_EVENT_GARBAGE_COLLECTION_FINISH);
}

void MemoryAgent::LogGcStart() { last_gc_start_ns_ = clock_.GetCurrentTime(); }

void MemoryAgent::LogGcFinish() {
  profiler::EnqueueGcStats(last_gc_start_ns_, clock_.GetCurrentTime());
}

void MemoryAgent::GCStartCallback(jvmtiEnv* jvmti) { agent_->LogGcStart(); }

void MemoryAgent::GCFinishCallback(jvmtiEnv* jvmti) { agent_->LogGcFinish(); }

}  // namespace profiler
