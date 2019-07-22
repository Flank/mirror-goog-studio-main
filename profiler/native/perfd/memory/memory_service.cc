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
#include "memory_service.h"

#include <unistd.h>

#include <cassert>

#include "proto/internal_memory.grpc.pb.h"
#include "utils/clock.h"
#include "utils/trace.h"

using profiler::proto::AllocationContextsResponse;
using profiler::proto::AllocationsInfo;
using profiler::proto::ForceGarbageCollectionRequest;
using profiler::proto::ForceGarbageCollectionResponse;
using profiler::proto::HeapDumpStatus;
using profiler::proto::ListDumpInfosRequest;
using profiler::proto::ListHeapDumpInfosResponse;
using profiler::proto::MemoryControlRequest;
using profiler::proto::MemoryData;
using profiler::proto::MemoryRequest;
using profiler::proto::MemoryStartRequest;
using profiler::proto::MemoryStartResponse;
using profiler::proto::MemoryStopRequest;
using profiler::proto::MemoryStopResponse;
using profiler::proto::Session;
using profiler::proto::SetAllocationSamplingRateRequest;
using profiler::proto::SetAllocationSamplingRateResponse;
using profiler::proto::TrackAllocationsRequest;
using profiler::proto::TrackAllocationsResponse;
using profiler::proto::TrackStatus;
using profiler::proto::TriggerHeapDumpRequest;
using profiler::proto::TriggerHeapDumpResponse;

// Retry SendRequestToAgent for 5 seconds in case the agent control stream was
// not yet initialized.
const int32_t kAgentReqRetryCount = 20;
const uint64_t kAgentReqRetryIntervalUs = profiler::Clock::ms_to_us(250);

namespace profiler {

grpc::Status MemoryServiceImpl::StartMonitoringApp(
    ::grpc::ServerContext* context, const MemoryStartRequest* request,
    MemoryStartResponse* response) {
  MemoryCollector* collector = GetCollector(request->session());
  if (!collector->IsRunning()) {
    collector->Start();
  }
  response->set_status(MemoryStartResponse::SUCCESS);
  return ::grpc::Status::OK;
}

grpc::Status MemoryServiceImpl::StopMonitoringApp(
    ::grpc::ServerContext* context, const MemoryStopRequest* request,
    MemoryStopResponse* response) {
  auto got = collectors_.find(request->session().pid());
  if (got != collectors_.end()) {
    if (got->second.IsRunning()) {
      got->second.Stop();
    }
    collectors_.erase(got);
  }
  response->set_status(MemoryStopResponse::SUCCESS);
  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetData(::grpc::ServerContext* context,
                                          const MemoryRequest* request,
                                          MemoryData* response) {
  Trace trace("MEM:GetData");
  auto result = collectors_.find(request->session().pid());
  if (result == collectors_.end()) {
    return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,
                          "The memory collector for the specified session has "
                          "not been started yet.");
  }

  result->second.memory_cache()->LoadMemoryData(request->start_time(),
                                                request->end_time(), response);

  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetJvmtiData(::grpc::ServerContext* context,
                                               const MemoryRequest* request,
                                               MemoryData* response) {
  Trace trace("MEM:GetJvmtiData");
  auto result = collectors_.find(request->session().pid());
  if (result == collectors_.end()) {
    return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,
                          "The memory collector for the specified session has "
                          "not been started yet.");
  }

  result->second.memory_cache()->LoadMemoryJvmtiData(
      request->start_time(), request->end_time(), response);

  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::SetAllocationSamplingRate(
    ::grpc::ServerContext* context,
    const SetAllocationSamplingRateRequest* request,
    SetAllocationSamplingRateResponse* reponse) {
  Trace trace("MEM:SetAllocationSamplingRate");
  MemoryControlRequest control_request;
  control_request.set_pid(request->session().pid());
  MemoryControlRequest::SetSamplingRate* set_sampling_rate_request =
      control_request.mutable_set_sampling_rate_request();
  set_sampling_rate_request->mutable_sampling_rate()->set_sampling_num_interval(
      request->sampling_rate().sampling_num_interval());
  if (!private_service_->SendRequestToAgent(control_request)) {
    return ::grpc::Status(::grpc::StatusCode::UNKNOWN,
                          "Unable to update live allocation sampling rate.");
  }
  return ::grpc::Status::OK;
}

#define PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(              \
    result, collectors, response, status)                                     \
  {                                                                           \
    if ((result) == (collectors).end()) {                                     \
      (response)->set_status(status);                                         \
      return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,                    \
                            "The memory collector for the specified session " \
                            "has not been started yet.");                     \
    }                                                                         \
  }

::grpc::Status MemoryServiceImpl::TriggerHeapDump(
    ::grpc::ServerContext* context, const TriggerHeapDumpRequest* request,
    TriggerHeapDumpResponse* response) {
  Trace trace("MEM:TriggerHeapDump");
  int32_t pid = request->session().pid();
  auto result = collectors_.find(pid);
  auto* status = response->mutable_status();
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, status, HeapDumpStatus::NOT_PROFILING)

  auto& collector = result->second;
  if (collector.IsRunning()) {
    int64_t request_time = clock_->GetCurrentTime();

    auto* cache = collector.memory_cache();
    bool dump_started = heap_dumper_->TriggerHeapDump(
        pid, request_time, [this, cache](bool dump_success) {
          cache->EndHeapDump(clock_->GetCurrentTime(), dump_success);
        });
    if (dump_started) {
      cache->StartHeapDump(request_time, response);
      status->set_status(HeapDumpStatus::SUCCESS);
      status->set_start_time(request_time);
    } else {
      status->set_status(HeapDumpStatus::IN_PROGRESS);
    }
  } else {
    status->set_status(HeapDumpStatus::NOT_PROFILING);
  }

  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::TrackAllocations(
    ::grpc::ServerContext* context, const TrackAllocationsRequest* request,
    TrackAllocationsResponse* response) {
  Trace trace("MEM:TrackAllocations");
  auto result = collectors_.find(request->session().pid());
  auto* status = response->mutable_status();
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, status, TrackStatus::NOT_PROFILING)

  if ((result->second).IsRunning()) {
    // Legacy allocation tracking is handled in the perfd-proxy layer.
    // This code path should only be valid for post-O.
    assert(!request->legacy());

    // Forwards a control signal to perfa to toggle JVMTI-based tracking.
    MemoryControlRequest control_request;
    control_request.set_pid(request->session().pid());
    if (request->enabled()) {
      MemoryControlRequest::EnableTracking* enable_request =
          control_request.mutable_enable_request();
      enable_request->set_timestamp(request->request_time());
    } else {
      MemoryControlRequest::DisableTracking* disable_request =
          control_request.mutable_disable_request();
      disable_request->set_timestamp(request->request_time());
    }
    // Retry for 5 seconds before failing the RPC in case the control stream
    // wasn't initialized.
    int32_t retries = 0;
    bool req_result = false;
    while (!req_result && retries++ < kAgentReqRetryCount) {
      usleep(kAgentReqRetryIntervalUs);
      req_result = private_service_->SendRequestToAgent(control_request);
    }
    if (!req_result) {
      return ::grpc::Status(::grpc::StatusCode::UNKNOWN,
                            "Unable to start live allocation tracking.");
    }

    // If a signal was successful sent, update the AllocationsInfo sample
    // that we track in perfd.
    (result->second)
        .TrackAllocations(request->request_time(), request->enabled(),
                          request->legacy(), response);
    switch (response->status().status()) {
      case TrackStatus::SUCCESS:
      case TrackStatus::IN_PROGRESS:
      case TrackStatus::NOT_ENABLED:
        return ::grpc::Status::OK;
      default:
        return ::grpc::Status(
            ::grpc::StatusCode::UNKNOWN,
            "Unknown issues when attempting to set allocation tracking.");
    }
  } else {
    status->set_status(TrackStatus::NOT_PROFILING);
    return ::grpc::Status::OK;
  }
}

#undef PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND

MemoryCollector* MemoryServiceImpl::GetCollector(
    const proto::Session& session) {
  auto got = collectors_.find(session.pid());
  if (got == collectors_.end()) {
    // Use the forward version of pair to avoid defining a move constructor.
    auto emplace_result = collectors_.emplace(
        std::piecewise_construct, std::forward_as_tuple(session.pid()),
        std::forward_as_tuple(session.pid(), clock_, file_cache_));
    assert(emplace_result.second);
    got = emplace_result.first;
  }
  return &got->second;
}

}  // namespace profiler
