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
#ifndef PERFD_MEMORY_INTERNAL_MEMORY_SERVICE_H_
#define PERFD_MEMORY_INTERNAL_MEMORY_SERVICE_H_

#include <grpc++/grpc++.h>
#include <unordered_map>

#include "memory_collector.h"
#include "proto/internal_memory.grpc.pb.h"

namespace profiler {

class InternalMemoryServiceImpl final
    : public proto::InternalMemoryService::Service {
 public:
  explicit InternalMemoryServiceImpl(
      std::unordered_map<int32_t, MemoryCollector> *collectors)
      : collectors_(*collectors) {}
  virtual ~InternalMemoryServiceImpl() = default;

  grpc::Status RecordAllocStats(grpc::ServerContext *context,
                                const proto::AllocStatsRequest *request,
                                proto::EmptyMemoryReply *reply) override;

  grpc::Status RecordGcStats(grpc::ServerContext *context,
                             const proto::GcStatsRequest *request,
                             proto::EmptyMemoryReply *reply) override;

 private:
  std::unordered_map<int32_t, MemoryCollector>
      &collectors_;  // maps pid to MemoryCollector
};

}  // namespace profiler

#endif  // PERFD_MEMORY_INTERNAL_MEMORY_SERVICE_H_
