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

#include <cassert>

#include "proto/internal_memory.grpc.pb.h"
#include "utils/trace.h"

using profiler::proto::AllocationsInfo;
using profiler::proto::DumpDataResponse;
using profiler::proto::DumpDataRequest;
using profiler::proto::ForceGarbageCollectionRequest;
using profiler::proto::ForceGarbageCollectionResponse;
using profiler::proto::MemoryControlRequest;
using profiler::proto::MemoryStartRequest;
using profiler::proto::MemoryStartResponse;
using profiler::proto::MemoryStopRequest;
using profiler::proto::MemoryStopResponse;
using profiler::proto::MemoryRequest;
using profiler::proto::MemoryData;
using profiler::proto::LegacyAllocationContextsRequest;
using profiler::proto::LegacyAllocationContextsResponse;
using profiler::proto::LegacyAllocationEventsRequest;
using profiler::proto::LegacyAllocationEventsResponse;
using profiler::proto::ListHeapDumpInfosResponse;
using profiler::proto::ListDumpInfosRequest;
using profiler::proto::TrackAllocationsRequest;
using profiler::proto::TrackAllocationsResponse;
using profiler::proto::TriggerHeapDumpRequest;
using profiler::proto::TriggerHeapDumpResponse;

namespace profiler {

grpc::Status MemoryServiceImpl::StartMonitoringApp(
    ::grpc::ServerContext* context, const MemoryStartRequest* request,
    MemoryStartResponse* response) {
  MemoryCollector* collector = GetCollector(request->process_id());
  if (!collector->IsRunning()) {
    collector->Start();
  }
  response->set_status(MemoryStartResponse::SUCCESS);
  return ::grpc::Status::OK;
}

grpc::Status MemoryServiceImpl::StopMonitoringApp(
    ::grpc::ServerContext* context, const MemoryStopRequest* request,
    MemoryStopResponse* response) {
  auto got = collectors_.find(request->process_id());
  if (got != collectors_.end() && got->second.IsRunning()) {
    got->second.Stop();
  }
  response->set_status(MemoryStopResponse::SUCCESS);
  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetData(::grpc::ServerContext* context,
                                          const MemoryRequest* request,
                                          MemoryData* response) {
  Trace trace("MEM:GetData");
  auto result = collectors_.find(request->process_id());
  if (result == collectors_.end()) {
    return ::grpc::Status(
        ::grpc::StatusCode::NOT_FOUND,
        "The memory collector for the specified pid has not been started yet.");
  }

  result->second.memory_cache()->LoadMemoryData(request->start_time(),
                                                request->end_time(), response);

  return ::grpc::Status::OK;
}

#define PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(              \
    result, collectors, response, status)                                     \
  {                                                                           \
    if ((result) == (collectors).end()) {                                     \
      (response)->set_status(status);                                         \
      return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,                    \
                            "The memory collector for the specified pid has " \
                            "not been started yet.");                         \
    }                                                                         \
  }

::grpc::Status MemoryServiceImpl::TriggerHeapDump(
    ::grpc::ServerContext* context, const TriggerHeapDumpRequest* request,
    TriggerHeapDumpResponse* response) {
  Trace trace("MEM:TriggerHeapDump");
  int32_t app_id = request->process_id();

  auto result = collectors_.find(app_id);
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, response, TriggerHeapDumpResponse::FAILURE_UNKNOWN)

  if ((result->second).IsRunning()) {
    if ((result->second).TriggerHeapDump(response)) {
      response->set_status(TriggerHeapDumpResponse::SUCCESS);
    } else {
      response->set_status(TriggerHeapDumpResponse::IN_PROGRESS);
    }
  } else {
    response->set_status(TriggerHeapDumpResponse::NOT_PROFILING);
  }

  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetHeapDump(::grpc::ServerContext* context,
                                              const DumpDataRequest* request,
                                              DumpDataResponse* response) {
  Trace trace("MEM:GetHeapDump");
  int32_t app_id = request->process_id();

  auto result = collectors_.find(app_id);
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, response, DumpDataResponse::FAILURE_UNKNOWN)

  (result->second).GetHeapDumpData(request->dump_time(), response);
  switch (response->status()) {
    case DumpDataResponse::NOT_READY:
    case DumpDataResponse::SUCCESS:
      return ::grpc::Status::OK;
    case DumpDataResponse::NOT_FOUND:
      return ::grpc::Status(::grpc::StatusCode::NOT_FOUND,
                            "The requested file_id was not matched to a file.");
    default:
      return ::grpc::Status(::grpc::StatusCode::UNKNOWN,
                            "Unknown issue when attempting to retrieve file.");
  }
}

::grpc::Status MemoryServiceImpl::ListHeapDumpInfos(
    ::grpc::ServerContext* context, const ListDumpInfosRequest* request,
    ListHeapDumpInfosResponse* response) {
  return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                        "Not implemented on device");
}

::grpc::Status MemoryServiceImpl::TrackAllocations(
    ::grpc::ServerContext* context, const TrackAllocationsRequest* request,
    TrackAllocationsResponse* response) {
  Trace trace("MEM:TrackAllocations");
  int32_t app_id = request->process_id();

  auto result = collectors_.find(app_id);
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, response, TrackAllocationsResponse::FAILURE_UNKNOWN)

  if ((result->second).IsRunning()) {
    // Legacy allocation tracking is handled in the perfd-proxy layer.
    // This code path should only be valid for post-O.
    assert(!request->legacy());

    // Forwards a control signal to perfa to toggle JVMTI-based tracking.
    MemoryControlRequest control_request;
    control_request.set_pid(app_id);
    if (request->enabled()) {
      MemoryControlRequest::EnableTracking* enable_request =
          control_request.mutable_enable_request();
      enable_request->set_timestamp(request->request_time());
    } else {
      MemoryControlRequest::DisableTracking* disable_request =
          control_request.mutable_disable_request();
      disable_request->set_timestamp(request->request_time());
    }
    if (!private_service_->SendRequestToAgent(control_request)) {
      return ::grpc::Status(::grpc::StatusCode::UNKNOWN,
                            "Unable to start live allocation tracking.");
    }

    // If a signal was successful sent, update the AllocationsInfo sample
    // that we track in perfd.
    (result->second)
        .TrackAllocations(request->request_time(), request->enabled(),
                          request->legacy(), response);
    switch (response->status()) {
      case TrackAllocationsResponse::SUCCESS:
      case TrackAllocationsResponse::IN_PROGRESS:
      case TrackAllocationsResponse::NOT_ENABLED:
        return ::grpc::Status::OK;
      default:
        return ::grpc::Status(
            ::grpc::StatusCode::UNKNOWN,
            "Unknown issues when attempting to set allocation tracking.");
    }
  } else {
    response->set_status(TrackAllocationsResponse::NOT_PROFILING);
    return ::grpc::Status::OK;
  }
}

#undef PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND

::grpc::Status MemoryServiceImpl::ListLegacyAllocationContexts(
    ::grpc::ServerContext* context,
    const LegacyAllocationContextsRequest* request,
    LegacyAllocationContextsResponse* response) {
  return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                        "Not implemented on device");
}

::grpc::Status MemoryServiceImpl::GetLegacyAllocationEvents(
    ::grpc::ServerContext* context,
    const LegacyAllocationEventsRequest* request,
    LegacyAllocationEventsResponse* response) {
  return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                        "Not implemented on device");
}

::grpc::Status MemoryServiceImpl::GetLegacyAllocationDump(
    ::grpc::ServerContext* context, const DumpDataRequest* request,
    DumpDataResponse* response) {
  return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                        "Not implemented on device");
}

::grpc::Status MemoryServiceImpl::ForceGarbageCollection(
    ::grpc::ServerContext* context,
    const ForceGarbageCollectionRequest* request,
    ForceGarbageCollectionResponse* response) {
  return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                        "Not implemented on device");
}

MemoryCollector* MemoryServiceImpl::GetCollector(int32_t app_id) {
  auto got = collectors_.find(app_id);
  if (got == collectors_.end()) {
    // Use the forward version of pair to avoid defining a move constructor.
    auto emplace_result = collectors_.emplace(
        std::piecewise_construct, std::forward_as_tuple(app_id),
        std::forward_as_tuple(app_id, clock_, file_cache_));
    assert(emplace_result.second);
    got = emplace_result.first;
  }
  return &got->second;
}

}  // namespace profiler
