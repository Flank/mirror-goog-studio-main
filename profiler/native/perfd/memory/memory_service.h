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
#ifndef PERFD_MEMORY_MEMORY_SERVICE_H_
#define PERFD_MEMORY_MEMORY_SERVICE_H_

#include <grpc++/grpc++.h>
#include <unordered_map>

#include "internal_memory_service.h"
#include "memory_collector.h"
#include "perfd/daemon.h"
#include "proto/common.pb.h"
#include "proto/memory.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class MemoryServiceImpl final
    : public ::profiler::proto::MemoryService::Service {
 public:
  MemoryServiceImpl(InternalMemoryServiceImpl* private_service, Clock* clock,
                    FileCache* file_cache,
                    std::unordered_map<int32_t, MemoryCollector>* collectors)
      : private_service_(private_service),
        clock_(clock),
        file_cache_(file_cache),
        collectors_(*collectors) {}
  virtual ~MemoryServiceImpl() = default;

  ::grpc::Status StartMonitoringApp(
      ::grpc::ServerContext* context,
      const profiler::proto::MemoryStartRequest* request,
      ::profiler::proto::MemoryStartResponse* response) override;

  ::grpc::Status StopMonitoringApp(
      ::grpc::ServerContext* context,
      const profiler::proto::MemoryStopRequest* request,
      ::profiler::proto::MemoryStopResponse* response) override;

  ::grpc::Status GetData(::grpc::ServerContext* context,
                         const ::profiler::proto::MemoryRequest* request,
                         ::profiler::proto::MemoryData* response) override;

  ::grpc::Status GetJvmtiData(::grpc::ServerContext* context,
                              const ::profiler::proto::MemoryRequest* request,
                              ::profiler::proto::MemoryData* response) override;

  ::grpc::Status TriggerHeapDump(
      ::grpc::ServerContext* context,
      const ::profiler::proto::TriggerHeapDumpRequest* request,
      ::profiler::proto::TriggerHeapDumpResponse* response) override;

  ::grpc::Status GetHeapDump(
      ::grpc::ServerContext* context,
      const ::profiler::proto::DumpDataRequest* request,
      ::profiler::proto::DumpDataResponse* response) override;

  ::grpc::Status TrackAllocations(
      ::grpc::ServerContext* context,
      const ::profiler::proto::TrackAllocationsRequest* request,
      ::profiler::proto::TrackAllocationsResponse* response) override;

  ::grpc::Status ListHeapDumpInfos(
      ::grpc::ServerContext* context,
      const ::profiler::proto::ListDumpInfosRequest* request,
      ::profiler::proto::ListHeapDumpInfosResponse* response) override {
    return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                          "Not implemented on device");
  }

  ::grpc::Status GetLegacyAllocationEvents(
      ::grpc::ServerContext* context,
      const ::profiler::proto::LegacyAllocationEventsRequest* request,
      ::profiler::proto::LegacyAllocationEventsResponse* response) override {
    return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                          "Not implemented on device");
  }

  ::grpc::Status GetLegacyAllocationContexts(
      ::grpc::ServerContext* context,
      const ::profiler::proto::LegacyAllocationContextsRequest* request,
      ::profiler::proto::AllocationContextsResponse* response) override {
    return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                          "Not implemented on device");
  }

  ::grpc::Status GetLegacyAllocationDump(
      ::grpc::ServerContext* context,
      const ::profiler::proto::DumpDataRequest* request,
      ::profiler::proto::DumpDataResponse* response) override {
    return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                          "Not implemented on device");
  }

  ::grpc::Status ForceGarbageCollection(
      ::grpc::ServerContext* context,
      const ::profiler::proto::ForceGarbageCollectionRequest* request,
      ::profiler::proto::ForceGarbageCollectionResponse* response) override {
    return ::grpc::Status(::grpc::StatusCode::UNIMPLEMENTED,
                          "Not implemented on device");
  }

 private:
  MemoryCollector* GetCollector(const proto::Session& session);

  InternalMemoryServiceImpl* private_service_;
  Clock* clock_;
  FileCache* file_cache_;
  // Maps pid to MemoryCollector
  std::unordered_map<int32_t, MemoryCollector>& collectors_;
};
}  // namespace profiler

#endif  // PERFD_MEMORY_MEMORY_SERVICE_H_
