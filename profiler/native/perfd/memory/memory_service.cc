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

using namespace ::profiler::proto;

namespace profiler {

::grpc::Status MemoryServiceImpl::SetMemoryConfig(
    ::grpc::ServerContext* context,
    const ::profiler::proto::MemoryConfig* request,
    MemoryStatus* response) {
  int32_t app_id = request->app_id();

  response->set_status_timestamp(clock_.GetCurrentTime());
  for (int i = 0; i < request->options_size(); i++) {
    const MemoryConfig_Option& option = request->options(i);
    switch (option.feature()) {
      case MemoryFeature::MEMORY_LEVELS:
        {
          auto got = collectors_.find(app_id);
          if (got == collectors_.end()) {
            // Use the forward version of pair to avoid defining a move constructor.
            auto emplace_result = collectors_.emplace(
                std::piecewise_construct,
                std::forward_as_tuple(app_id),
                std::forward_as_tuple(app_id, clock_));
            assert(emplace_result.second);
            got = emplace_result.first;
          }
          MemoryCollector& collector = got->second;

          if (option.enabled()) {
            collector.Start();
          }
          else {
            collector.Stop();
          }
        }
        break;

      default:
        return ::grpc::Status(::grpc::StatusCode::NOT_FOUND, "Memory feature not handled.");
    }
  }

  return ::grpc::Status::OK;
}

::grpc::Status MemoryServiceImpl::GetData(
      ::grpc::ServerContext* context,
      const MemoryRequest* request,
      MemoryData* response) {
  int64_t current_time = clock_.GetCurrentTime();

  int32_t app_id = request->app_id();
  int64_t start_time = request->start_time();
  int64_t end_time = request->end_time();

  response->set_end_timestamp(std::min(current_time, end_time));
  auto result = collectors_.find(app_id);
  if (result == collectors_.end()) {
    return ::grpc::Status(::grpc::StatusCode::NOT_FOUND, "The memory collector for the specified pid has not been started yet.");
  }

  result->second.memory_cache()->LoadMemorySamples(start_time, end_time, response);
  result->second.memory_cache()->LoadInstanceCountSamples(start_time, end_time, response);
  result->second.memory_cache()->LoadGcSamples(start_time, end_time, response);

  return ::grpc::Status::OK;
}

} // namespace profiler