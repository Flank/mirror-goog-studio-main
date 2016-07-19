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
#include "perfd/event/event_service.h"

#include <grpc++/grpc++.h>
#include <vector>

#include "utils/trace.h"
#include "perfd/event/event_cache.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::proto::EventDataRequest;
using profiler::proto::EventDataResponse;
using std::vector;

namespace profiler {
EventServiceImpl::EventServiceImpl(EventCache& cache) : cache_(cache) {}

Status EventServiceImpl::GetData(ServerContext* context,
                                 const EventDataRequest* request,
                                 EventDataResponse* response) {
  Trace trace("EVT:GetData");
  Status status = Status::OK;
  int64_t startTime = request->start_timestamp();
  int64_t endTime = request->end_timestamp();
  cache_.GetData(startTime, endTime, response);
  return status;
}

}  // namespace profiler
