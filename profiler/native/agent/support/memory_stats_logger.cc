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
 *
 */
#include "memory_stats_logger.h"

#include <jni.h>

#include "agent/agent.h"
#include "utils/clock.h"
#include "utils/log.h"

using grpc::ClientContext;
using profiler::SteadyClock;
using profiler::Agent;
using profiler::proto::EmptyMemoryReply;
using profiler::proto::AllocStatsRequest;
using profiler::proto::GcStatsRequest;
using profiler::proto::MemoryData;

namespace {
const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}
}  // namespace

namespace profiler {

void EnqueueAllocStats(int32_t alloc_count, int32_t free_count) {
  int64_t timestamp = GetClock().GetCurrentTime();

  int32_t pid = getpid();
  Agent::Instance().background_queue()->EnqueueTask(
      [alloc_count, free_count, pid, timestamp]() {
        auto mem_stub = Agent::Instance().memory_component()->service_stub();

        ClientContext context;
        EmptyMemoryReply reply;

        AllocStatsRequest alloc_stats_request;
        alloc_stats_request.set_process_id(pid);

        MemoryData::AllocStatsSample* stats =
            alloc_stats_request.mutable_alloc_stats_sample();
        stats->set_timestamp(timestamp);
        stats->set_java_allocation_count(alloc_count);
        stats->set_java_free_count(free_count);

        mem_stub.RecordAllocStats(&context, alloc_stats_request, &reply);
      });
}

void EnqueueGcStats(int64_t start_time, int64_t end_time) {
  int32_t pid = getpid();
  Agent::Instance().background_queue()->EnqueueTask(
      [start_time, end_time, pid]() {
        auto mem_stub = Agent::Instance().memory_component()->service_stub();

        ClientContext context;
        EmptyMemoryReply reply;

        GcStatsRequest gc_stats_request;
        gc_stats_request.set_process_id(pid);

        MemoryData::GcStatsSample* stats =
            gc_stats_request.mutable_gc_stats_sample();
        stats->set_start_time(start_time);
        stats->set_end_time(end_time);
        mem_stub.RecordGcStats(&context, gc_stats_request, &reply);
      });
}

void EnqueueAllocationEvents(proto::BatchAllocationSample& request) {
  request.set_process_id(getpid());
  Agent::Instance().background_queue()->EnqueueTask([request]() {
    auto mem_stub = Agent::Instance().memory_component()->service_stub();
    ClientContext context;
    EmptyMemoryReply reply;
    // Note: The sample's timestamp is set once it reaches perfd, to avoid
    // the sample being missed during MemoryCache::LoadMemoryData. This can
    // occur if there is enough delay between here and when the sample gets
    // saved in the cache, and other memory data samples with later timestamps
    // have already been queried during LoadMemoryData.
    mem_stub.RecordAllocationEvents(&context, request, &reply);
  });
}

}  // namespace profiler

extern "C" {

// JNI entry point for logging alloc stats in pre-O.
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_memory_VmStatsSampler_logAllocStats(
    JNIEnv* env, jclass clazz, jint jallocCount, jint jfreeCount) {
  profiler::EnqueueAllocStats(jallocCount, jfreeCount);
}

// JNI entry point for logging gc stats in pre-O..
JNIEXPORT void JNICALL
Java_com_android_tools_profiler_support_memory_VmStatsSampler_logGcStats(
    JNIEnv* env, jclass clazz) {
  int64_t timestamp = GetClock().GetCurrentTime();
  profiler::EnqueueGcStats(timestamp, timestamp);
}
};
