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

#include <cassert>
#include <climits>
#include <vector>

#include "agent/agent.h"
#include "utils/clock.h"
#include "utils/log.h"

using grpc::ClientContext;
using grpc::Status;
using profiler::Agent;
using profiler::SteadyClock;
using profiler::proto::AgentService;
using profiler::proto::AllocationEventsRequest;
using profiler::proto::AllocationSamplingRateEventRequest;
using profiler::proto::AllocStatsRequest;
using profiler::proto::EmptyMemoryReply;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::GcStatsRequest;
using profiler::proto::InternalMemoryService;
using profiler::proto::JNIRefEventsRequest;
using profiler::proto::MemoryData;
using profiler::proto::SendEventRequest;
using profiler::proto::TrackStatus;

namespace {
const SteadyClock& GetClock() {
  static SteadyClock clock;
  return clock;
}
}  // namespace

namespace profiler {

void EnqueueAllocStats(int32_t alloc_count, int32_t free_count) {
  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[alloc_count, free_count](AgentService::Stub& stub,
                                   ClientContext& ctx) {
          SendEventRequest request;
          auto* event = request.mutable_event();
          event->set_pid(getpid());
          event->set_kind(Event::MEMORY_ALLOC_STATS);

          auto* stats = event->mutable_memory_alloc_stats();
          stats->set_java_allocation_count(alloc_count);
          stats->set_java_free_count(free_count);

          EmptyResponse response;
          return stub.SendEvent(&ctx, request, &response);
        }});
  } else {
    int64_t timestamp = GetClock().GetCurrentTime();
    Agent::Instance().wait_and_get_memory_component().SubmitMemoryTasks(
        {[alloc_count, free_count, timestamp](InternalMemoryService::Stub& stub,
                                              ClientContext& ctx) {
          AllocStatsRequest alloc_stats_request;
          alloc_stats_request.set_pid(getpid());
          auto* sample = alloc_stats_request.mutable_alloc_stats_sample();
          sample->set_timestamp(timestamp);
          auto* stats = sample->mutable_alloc_stats();
          stats->set_java_allocation_count(alloc_count);
          stats->set_java_free_count(free_count);

          EmptyMemoryReply reply;
          return stub.RecordAllocStats(&ctx, alloc_stats_request, &reply);
        }});
  }
}

void EnqueueGcStats(int64_t start_time, int64_t end_time) {
  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[start_time, end_time](AgentService::Stub& stub, ClientContext& ctx) {
          SendEventRequest request;
          auto* event = request.mutable_event();
          event->set_pid(getpid());
          event->set_kind(Event::MEMORY_GC);
          event->set_timestamp(start_time);

          auto* data = event->mutable_memory_gc();
          data->set_duration(end_time - start_time);

          EmptyResponse response;
          return stub.SendEvent(&ctx, request, &response);
        }});
  } else {
    Agent::Instance().wait_and_get_memory_component().SubmitMemoryTasks(
        {[start_time, end_time](InternalMemoryService::Stub& stub,
                                ClientContext& ctx) {
          GcStatsRequest gc_stats_request;
          gc_stats_request.set_pid(getpid());
          MemoryData::GcStatsSample* stats =
              gc_stats_request.mutable_gc_stats_sample();
          stats->set_start_time(start_time);
          stats->set_end_time(end_time);

          EmptyMemoryReply reply;
          return stub.RecordGcStats(&ctx, gc_stats_request, &reply);
        }});
  }
}

void EnqueueAllocationInfoEvents(const proto::Command& command,
                                 int64_t track_start_timestamp,
                                 bool command_success) {
  assert(Agent::Instance().agent_config().common().profiler_unified_pipeline());

  bool is_start_command = command.has_start_alloc_tracking();
  bool request_timestamp = is_start_command
                               ? command.start_alloc_tracking().request_time()
                               : command.stop_alloc_tracking().request_time();

  // Task for sending the MEMORY_ALLOC_TRACKING_STATUS event.
  Agent::Instance().SubmitAgentTasks(
      {[command, track_start_timestamp, command_success, is_start_command](
           AgentService::Stub& stub, ClientContext& ctx) {
        SendEventRequest request;
        auto* event = request.mutable_event();
        event->set_pid(getpid());
        event->set_kind(Event::MEMORY_ALLOC_TRACKING_STATUS);
        event->set_command_id(command.command_id());
        auto* status =
            event->mutable_memory_alloc_tracking_status()->mutable_status();
        status->set_start_time(track_start_timestamp);
        if (command_success) {
          status->set_status(TrackStatus::SUCCESS);
        } else {
          status->set_status(is_start_command ? TrackStatus::IN_PROGRESS
                                              : TrackStatus::NOT_ENABLED);
        }

        EmptyResponse response;
        return stub.SendEvent(&ctx, request, &response);
      }});

  // Task for sending the MEMORY_ALLOC_TRACKING event.
  if (command_success) {
    Agent::Instance().SubmitAgentTasks(
        {[command, track_start_timestamp, is_start_command, request_timestamp](
             AgentService::Stub& stub, ClientContext& ctx) {
          SendEventRequest request;
          auto* event = request.mutable_event();
          event->set_pid(getpid());
          event->set_kind(Event::MEMORY_ALLOC_TRACKING);
          event->set_group_id(track_start_timestamp);
          auto* info = event->mutable_memory_alloc_tracking()->mutable_info();
          info->set_start_time(track_start_timestamp);
          if (is_start_command) {
            // start event.
            info->set_end_time(LLONG_MAX);
          } else {
            // end event.
            event->set_is_ended(true);
            info->set_end_time(request_timestamp);
            info->set_success(true);
          }

          EmptyResponse response;
          return stub.SendEvent(&ctx, request, &response);
        }});
  }
}

void EnqueueAllocationEvents(const proto::BatchAllocationContexts& contexts,
                             const proto::BatchAllocationEvents& events) {
  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[contexts](AgentService::Stub& stub, ClientContext& ctx) {
           SendEventRequest request;
           auto* event = request.mutable_event();
           event->set_pid(getpid());
           event->set_kind(Event::MEMORY_ALLOC_CONTEXTS);
           auto* data = event->mutable_memory_alloc_contexts();
           data->mutable_contexts()->CopyFrom(contexts);

           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         },
         [events](AgentService::Stub& stub, ClientContext& ctx) {
           SendEventRequest request;
           auto* event = request.mutable_event();
           event->set_pid(getpid());
           event->set_kind(Event::MEMORY_ALLOC_EVENTS);
           auto* data = event->mutable_memory_alloc_events();
           data->mutable_events()->CopyFrom(events);

           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         }});
  } else {
    AllocationEventsRequest request;
    request.set_pid(getpid());
    request.mutable_contexts()->CopyFrom(contexts);
    request.mutable_events()->CopyFrom(events);
    Agent::Instance().wait_and_get_memory_component().SubmitMemoryTasks(
        {[request](InternalMemoryService::Stub& stub, ClientContext& ctx) {
          EmptyMemoryReply reply;
          return stub.RecordAllocationEvents(&ctx, request, &reply);
        }});
  }
}

void EnqueueJNIGlobalRefEvents(const proto::BatchAllocationContexts& contexts,
                               const proto::BatchJNIGlobalRefEvent& events) {
  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[contexts](AgentService::Stub& stub, ClientContext& ctx) {
           SendEventRequest request;
           auto* event = request.mutable_event();
           event->set_pid(getpid());
           event->set_kind(Event::MEMORY_ALLOC_CONTEXTS);
           auto* data = event->mutable_memory_alloc_contexts();
           data->mutable_contexts()->CopyFrom(contexts);

           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         },
         [events](AgentService::Stub& stub, ClientContext& ctx) {
           SendEventRequest request;
           auto* event = request.mutable_event();
           event->set_pid(getpid());
           event->set_kind(Event::MEMORY_JNI_REF_EVENTS);
           auto* data = event->mutable_memory_jni_ref_events();
           data->mutable_events()->CopyFrom(events);

           EmptyResponse response;
           return stub.SendEvent(&ctx, request, &response);
         }});
  } else {
    JNIRefEventsRequest request;
    request.set_pid(getpid());
    request.mutable_contexts()->CopyFrom(contexts);
    request.mutable_events()->CopyFrom(events);
    Agent::Instance().wait_and_get_memory_component().SubmitMemoryTasks(
        {[request](InternalMemoryService::Stub& stub, ClientContext& ctx) {
          EmptyMemoryReply reply;
          return stub.RecordJNIRefEvents(&ctx, request, &reply);
        }});
  }
}

void EnqueueAllocationSamplingRateEvent(int64_t timestamp,
                                        int32_t sampling_num_interval) {
  if (Agent::Instance().agent_config().common().profiler_unified_pipeline()) {
    Agent::Instance().SubmitAgentTasks(
        {[sampling_num_interval](AgentService::Stub& stub, ClientContext& ctx) {
          SendEventRequest request;
          auto* event = request.mutable_event();
          event->set_pid(getpid());
          event->set_kind(Event::MEMORY_ALLOC_SAMPLING);

          auto* data = event->mutable_memory_alloc_sampling();
          data->set_sampling_num_interval(sampling_num_interval);

          EmptyResponse response;
          return stub.SendEvent(&ctx, request, &response);
        }});
  } else {
    Agent::Instance().wait_and_get_memory_component().SubmitMemoryTasks(
        {[timestamp, sampling_num_interval](InternalMemoryService::Stub& stub,
                                            ClientContext& ctx) {
          AllocationSamplingRateEventRequest request;
          request.set_pid(getpid());
          auto event = request.mutable_event();
          event->set_timestamp(timestamp);
          event->mutable_sampling_rate()->set_sampling_num_interval(
              sampling_num_interval);

          EmptyMemoryReply reply;
          return stub.RecordAllocationSamplingRateEvent(&ctx, request, &reply);
        }});
  }
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
