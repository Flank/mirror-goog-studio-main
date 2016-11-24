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

#include "utils/trace.h"

using profiler::proto::AllocationTrackingEnvironmentRequest;
using profiler::proto::AllocationTrackingEnvironmentResponse;
using profiler::proto::AllocationTrackingRequest;
using profiler::proto::AllocationTrackingResponse;
using profiler::proto::DumpDataResponse;
using profiler::proto::HeapDumpDataRequest;
using profiler::proto::TriggerHeapDumpRequest;
using profiler::proto::TriggerHeapDumpResponse;
using profiler::proto::ListHeapDumpInfosResponse;
using profiler::proto::ListDumpInfosRequest;
using profiler::proto::MemoryData;
using profiler::proto::MemoryRequest;
using profiler::proto::MemoryStartRequest;
using profiler::proto::MemoryStartResponse;
using profiler::proto::MemoryStopRequest;
using profiler::proto::MemoryStopResponse;

namespace profiler {

grpc::Status MemoryServiceImpl::StartMonitoringApp(::grpc::ServerContext* context,
                                                const MemoryStartRequest* request,
                                                MemoryStartResponse* response) {
  GetCollector(request->app_id())->Start();
  response->set_status(MemoryStartResponse::SUCCESS);
  return ::grpc::Status::OK;
}

grpc::Status MemoryServiceImpl::StopMonitoringApp(::grpc::ServerContext* context,
                                               const MemoryStopRequest* request,
                                               MemoryStopResponse* response) {
  auto got = collectors_.find(request->app_id());
  if (got != collectors_.end()) {
    got->second.Stop();
  }
  response->set_status(MemoryStopResponse::SUCCESS);
  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetData(::grpc::ServerContext* context,
                                          const MemoryRequest* request,
                                          MemoryData* response) {
  Trace trace("MEM:GetData");
  auto result = collectors_.find(request->app_id());
  if (result == collectors_.end()) {
    return ::grpc::Status(
        ::grpc::StatusCode::NOT_FOUND,
        "The memory collector for the specified pid has not been started yet.");
  }

  result->second.memory_cache()->LoadMemoryData(request->start_time(),
                                                request->end_time(), response);

  return ::grpc::Status::OK;
}

#define PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS( \
    result, collectors, response, status) { \
  if ((result) == (collectors).end()) { \
    (response)->set_status(status); \
    return ::grpc::Status( \
        ::grpc::StatusCode::NOT_FOUND, \
        "The memory collector for the specified pid has not been started yet."); \
  } \
}

::grpc::Status MemoryServiceImpl::TriggerHeapDump(
    ::grpc::ServerContext* context, const TriggerHeapDumpRequest* request,
    TriggerHeapDumpResponse* response) {
  Trace trace("MEM:TriggerHeapDump");
  int32_t app_id = request->app_id();

  auto result = collectors_.find(app_id);
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, response, TriggerHeapDumpResponse::FAILURE_UNKNOWN)

  if ((result->second).TriggerHeapDump()) {
    response->set_status(TriggerHeapDumpResponse::SUCCESS);
  } else {
    response->set_status(TriggerHeapDumpResponse::IN_PROGRESS);
  }

  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetHeapDump(
    ::grpc::ServerContext* context, const HeapDumpDataRequest* request,
    DumpDataResponse* response) {
  Trace trace("MEM:GetFile");
  int32_t app_id = request->app_id();

  auto result = collectors_.find(app_id);
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, response, DumpDataResponse::FAILURE_UNKNOWN)

  (result->second).GetHeapDumpData(request->dump_id(), response);
  switch (response->status()) {
    case DumpDataResponse::NOT_READY:
    case DumpDataResponse::SUCCESS:
      return ::grpc::Status::OK;
    case DumpDataResponse::NOT_FOUND:
      return ::grpc::Status(
          ::grpc::StatusCode::NOT_FOUND,
          "The requested file_id was not matched to a file.");
    default:
      return ::grpc::Status(
          ::grpc::StatusCode::UNKNOWN,
          "Unknown issue when attempting to retrieve file.");
  }
}

::grpc::Status MemoryServiceImpl::ListHeapDumpInfos(
    ::grpc::ServerContext* context, const ListDumpInfosRequest* request,
    ListHeapDumpInfosResponse* response) {
    return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                          "Not implemented on device");
}

::grpc::Status MemoryServiceImpl::SetAllocationTracking(
    ::grpc::ServerContext* context, const AllocationTrackingRequest* request,
    AllocationTrackingResponse* response) {
  Trace trace("MEM:GetFile");
  int32_t app_id = request->app_id();

  auto result = collectors_.find(app_id);
  PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND_WITH_STATUS(
      result, collectors_, response, AllocationTrackingResponse::FAILURE_UNKNOWN)

  (result->second).SetAllocationTracking(request->enabled(), response);
  switch (response->status()) {
    case AllocationTrackingResponse::SUCCESS:
    case AllocationTrackingResponse::IN_PROGRESS:
    case AllocationTrackingResponse::NOT_ENABLED:
      return ::grpc::Status::OK;
    default:
      return ::grpc::Status(
          ::grpc::StatusCode::UNKNOWN,
          "Unknown issues when attempting to set allocation tracking.");
  }
}

#undef PROFILER_MEMORY_SERVICE_RETURN_IF_NOT_FOUND

::grpc::Status MemoryServiceImpl::ListAllocationTrackingEnvironments(
    ::grpc::ServerContext* context,
    const AllocationTrackingEnvironmentRequest* request,
    AllocationTrackingEnvironmentResponse* response) {
  return ::grpc::Status(
      ::grpc::StatusCode::UNIMPLEMENTED,
      "Listing allocation tracking environments is WIP.");
}

MemoryCollector* MemoryServiceImpl::GetCollector(int32_t app_id) {
  auto got = collectors_.find(app_id);
  if (got == collectors_.end()) {
    // Use the forward version of pair to avoid defining a move constructor.
    auto emplace_result = collectors_.emplace(
        std::piecewise_construct, std::forward_as_tuple(app_id),
        std::forward_as_tuple(app_id, clock_));
    assert(emplace_result.second);
    got = emplace_result.first;
  }
  return &got->second;
}

}  // namespace profiler
