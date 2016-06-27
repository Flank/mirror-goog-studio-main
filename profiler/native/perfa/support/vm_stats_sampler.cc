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

#include "perfa/perfa.h"
#include "utils/clock.h"
#include "utils/stopwatch.h"

using grpc::ClientContext;
using profiler::SteadyClock;
using profiler::Perfa;
using profiler::proto::EmptyMemoryReply;
using profiler::proto::VmStatsRequest;
using profiler::proto::MemoryData;

namespace {

const int32_t kPid = getpid();

const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}

void SendVmStats(int32_t alloc_count, int32_t free_count, int32_t gc_count) {
  auto mem_stub = Perfa::Instance().memory_stub();

  ClientContext context;
  EmptyMemoryReply reply;

  VmStatsRequest vm_stats_request;
  vm_stats_request.set_app_id(kPid);

  MemoryData::VmStatsSample* stats = vm_stats_request.mutable_vm_stats_sample();
  stats->set_timestamp(GetClock().GetCurrentTime());
  stats->set_java_allocation_count(alloc_count);
  stats->set_java_free_count(free_count);
  stats->set_gc_count(gc_count);

  mem_stub.RecordVmStats(&context, vm_stats_request, &reply);
}

}  // namespace profiler

extern "C" {

JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_memory_VmStatsSampler_sendVmStats(
    JNIEnv* env, jclass clazz, jint jallocCount, jint jfreeCount,
    jint jgcCount) {
  SendVmStats(jallocCount, jfreeCount, jgcCount);
}
};
