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
#include "perfd/event/internal_event_service.h"

#include <grpc++/grpc++.h>
#include <vector>

#include "perfd/event/event_cache.h"
#include "utils/log.h"

using grpc::ServerContext;
using grpc::Status;
using profiler::EventCache;
using profiler::proto::EmptyEventResponse;
using profiler::proto::SendActivityDataRequest;
using profiler::proto::SendSystemDataRequest;

namespace profiler {

InternalEventServiceImpl::InternalEventServiceImpl(EventCache* cache)
    : cache_(*cache) {}

Status InternalEventServiceImpl::SendActivity(
    ServerContext* context, const SendActivityDataRequest* request,
    EmptyEventResponse* response) {
  // TODO: Validate incomming data has timestamp set, and at least one of
  // the event data type messages set.
  cache_.AddActivityData(request->pid(), request->data());
  return Status::OK;
}

Status InternalEventServiceImpl::SendSystem(
    ServerContext* context, const SendSystemDataRequest* request,
    EmptyEventResponse* response) {
  // TODO: Validate incomming data has timestamp set, and at least one of
  // the event data type messages set.
  cache_.AddSystemData(request->pid(), request->data());
  return Status::OK;
}

}  // namespace profiler
