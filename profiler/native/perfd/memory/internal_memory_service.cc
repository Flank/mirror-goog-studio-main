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
#include "internal_memory_service.h"

#include <grpc++/grpc++.h>
#include <unistd.h>

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

Status InternalMemoryServiceImpl::RecordAllocStats(
    ServerContext *context, const proto::AllocStatsRequest *request,
    proto::EmptyMemoryReply *reply) {
  auto result = collectors_.find(request->process_id());
  if (result == collectors_.end()) {
    return ::grpc::Status(
        ::grpc::StatusCode::NOT_FOUND,
        "The memory collector for the specified pid has not been started yet.");
  }

  result->second.memory_cache()->SaveAllocStatsSample(
      request->alloc_stats_sample());

  return Status::OK;
}

Status InternalMemoryServiceImpl::RecordGcStats(
    ServerContext *context, const proto::GcStatsRequest *request,
    proto::EmptyMemoryReply *reply) {
  auto result = collectors_.find(request->process_id());
  if (result == collectors_.end()) {
    return ::grpc::Status(
        ::grpc::StatusCode::NOT_FOUND,
        "The memory collector for the specified pid has not been started yet.");
  }

  result->second.memory_cache()->SaveGcStatsSample(request->gc_stats_sample());

  return Status::OK;
}

}  // namespace profiler
