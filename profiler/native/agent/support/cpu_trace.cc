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
#include "proto/internal_cpu.grpc.pb.h"

using grpc::ClientContext;
using profiler::Agent;
using profiler::JStringWrapper;
using profiler::SteadyClock;
using profiler::proto::CpuTraceOperationRequest;
using profiler::proto::EmptyCpuResponse;
using profiler::proto::InternalCpuService;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

// Enqueue and submit the target |request|. The request's pid and timestamp will
// be set as a side-effect of calling this method, but all other fields and
// appropriate metadata must be set by the caller.
void SubmitCpuTraceOperation(CpuTraceOperationRequest& request) {
  request.set_pid(getpid());
  request.set_timestamp(GetClock().GetCurrentTime());
  Agent::Instance().SubmitCpuTasks(
      {[request](InternalCpuService::Stub& stub, ClientContext& ctx) {
        EmptyCpuResponse response;
        return stub.SendTraceOperation(&ctx, request, &response);
      }});
}
}  // namespace

extern "C" {
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_sendStartOperation(
    JNIEnv* env, jclass clazz, jint thread_id, jstring trace_path) {
  CpuTraceOperationRequest request;
  request.set_thread_id(thread_id);
  request.set_api_name("StartMethodTracing");
  request.set_api_signature("(Ljava/lang/String;)V");
  JStringWrapper path_string(env, trace_path);
  request.add_arguments(path_string.get());
  SubmitCpuTraceOperation(request);
}

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_cpu_TraceOperationTracker_sendStopOperation(
    JNIEnv* env, jclass clazz, jint thread_id) {
  CpuTraceOperationRequest request;
  request.set_thread_id(thread_id);
  request.set_api_name("StopMethodTracing");
  request.set_api_signature("()V");
  SubmitCpuTraceOperation(request);
}
};