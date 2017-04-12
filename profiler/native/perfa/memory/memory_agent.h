/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef MEMORY_AGENT_H
#define MEMORY_AGENT_H

#include "jni.h"
#include "jvmti.h"

#include <unistd.h>

#include "proto/internal_memory.grpc.pb.h"
#include "utils/clock.h"

using profiler::Clock;
using profiler::proto::MemoryControlRequest;

namespace profiler {

class MemoryAgent {
 public:
  static MemoryAgent* Instance(JavaVM* vm);

 private:
  jvmtiEnv* jvmti_;
  SteadyClock clock_;
  int64_t last_gc_start_ns_;

  explicit MemoryAgent(jvmtiEnv* jvmti);

  // Agent is alive through the app's lifetime, don't bother cleaning up.
  ~MemoryAgent() = delete;
  MemoryAgent(const MemoryAgent&) = delete;
  MemoryAgent& operator=(const MemoryAgent&) = delete;

  void Initialize();
  void LogGcStart();
  void LogGcFinish();

  void HandleControlSignal(const MemoryControlRequest* request);

  // JVMTI Callback for garbage collection start events.
  static void JNICALL GCStartCallback(jvmtiEnv* jvmti);
  // JVMTI Callback for garbage collection end events.
  static void JNICALL GCFinishCallback(jvmtiEnv* jvmti);
};

}  // namespace profiler

#endif  // MEMORY_AGENT_H
