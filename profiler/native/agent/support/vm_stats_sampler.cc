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
#include "utils/clock.h"
#include "utils/stopwatch.h"

using grpc::ClientContext;
using profiler::SteadyClock;
using profiler::Agent;
using profiler::proto::EmptyMemoryReply;
using profiler::proto::VmStatsRequest;
using profiler::proto::MemoryData;

namespace {

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

void EnqueueVmStats(int32_t alloc_count, int32_t free_count, int32_t gc_count) {
  int64_t timestamp = GetClock().GetCurrentTime();

  int32_t pid = getpid();
  Agent::Instance().background_queue()->EnqueueTask(
      [alloc_count, free_count, gc_count, pid, timestamp]() {
        auto mem_stub = Agent::Instance().memory_stub();

        ClientContext context;
        EmptyMemoryReply reply;

        VmStatsRequest vm_stats_request;
        vm_stats_request.set_process_id(pid);

        MemoryData::VmStatsSample* stats =
            vm_stats_request.mutable_vm_stats_sample();
        stats->set_timestamp(timestamp);
        stats->set_java_allocation_count(alloc_count);
        stats->set_java_free_count(free_count);
        stats->set_gc_count(gc_count);

        mem_stub.RecordVmStats(&context, vm_stats_request, &reply);
      });
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_memory_VmStatsSampler_sendVmStats(
    JNIEnv* env, jclass clazz, jint jallocCount, jint jfreeCount,
    jint jgcCount) {
  EnqueueVmStats(jallocCount, jfreeCount, jgcCount);
}
};
