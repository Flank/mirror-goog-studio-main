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
using grpc::Status;
using profiler::Agent;
using profiler::SteadyClock;
using profiler::proto::AllocationSamplingRateEventRequest;
using profiler::proto::AllocStatsRequest;
using profiler::proto::EmptyMemoryReply;
using profiler::proto::GcStatsRequest;
using profiler::proto::InternalMemoryService;
using profiler::proto::MemoryData;

namespace {
const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}
}  // namespace

namespace profiler {

void EnqueueAllocStats(int32_t alloc_count, int32_t free_count) {
  if (Agent::Instance().agent_config().unified_pipeline()) {
    return;
  }

  int64_t timestamp = GetClock().GetCurrentTime();
  int32_t pid = getpid();
  Agent::Instance().memory_component().SubmitMemoryTasks(
      {[alloc_count, free_count, pid, timestamp](
           InternalMemoryService::Stub& stub, ClientContext& ctx) {
        AllocStatsRequest alloc_stats_request;
        alloc_stats_request.set_pid(pid);
        MemoryData::AllocStatsSample* stats =
            alloc_stats_request.mutable_alloc_stats_sample();
        stats->set_timestamp(timestamp);
        stats->set_java_allocation_count(alloc_count);
        stats->set_java_free_count(free_count);

        EmptyMemoryReply reply;
        return stub.RecordAllocStats(&ctx, alloc_stats_request, &reply);
      }});
}

void EnqueueGcStats(int64_t start_time, int64_t end_time) {
  if (Agent::Instance().agent_config().unified_pipeline()) {
    return;
  }

  int32_t pid = getpid();
  Agent::Instance().memory_component().SubmitMemoryTasks(
      {[start_time, end_time, pid](InternalMemoryService::Stub& stub,
                                   ClientContext& ctx) {
        GcStatsRequest gc_stats_request;
        gc_stats_request.set_pid(pid);
        MemoryData::GcStatsSample* stats =
            gc_stats_request.mutable_gc_stats_sample();
        stats->set_start_time(start_time);
        stats->set_end_time(end_time);

        EmptyMemoryReply reply;
        return stub.RecordGcStats(&ctx, gc_stats_request, &reply);
      }});
}

void EnqueueAllocationEvents(proto::BatchAllocationSample& request) {
  if (Agent::Instance().agent_config().unified_pipeline()) {
    return;
  }

  request.set_pid(getpid());
  Agent::Instance().memory_component().SubmitMemoryTasks(
      {[request](InternalMemoryService::Stub& stub, ClientContext& ctx) {
        EmptyMemoryReply reply;
        return stub.RecordAllocationEvents(&ctx, request, &reply);
      }});
}

void EnqueueJNIGlobalRefEvents(proto::BatchJNIGlobalRefEvent& request) {
  if (Agent::Instance().agent_config().unified_pipeline()) {
    return;
  }

  request.set_pid(getpid());
  Agent::Instance().memory_component().SubmitMemoryTasks(
      {[request](InternalMemoryService::Stub& stub, ClientContext& ctx) {
        EmptyMemoryReply reply;
        return stub.RecordJNIRefEvents(&ctx, request, &reply);
      }});
}

void EnqueueAllocationSamplingRateEvent(int64_t timestamp,
                                        int32_t sampling_num_interval) {
  if (Agent::Instance().agent_config().unified_pipeline()) {
    return;
  }

  int32_t pid = getpid();
  Agent::Instance().memory_component().SubmitMemoryTasks(
      {[timestamp, sampling_num_interval, pid](
           InternalMemoryService::Stub& stub, ClientContext& ctx) {
        AllocationSamplingRateEventRequest request;
        request.set_pid(pid);
        auto event = request.mutable_event();
        event->set_timestamp(timestamp);
        event->mutable_sampling_rate()->set_sampling_num_interval(
            sampling_num_interval);

        EmptyMemoryReply reply;
        return stub.RecordAllocationSamplingRateEvent(&ctx, request, &reply);
      }});
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
