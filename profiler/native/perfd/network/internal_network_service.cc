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
#include "internal_network_service.h"

#include <grpc++/grpc++.h>

#include "utils/log.h"

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

InternalNetworkServiceImpl::InternalNetworkServiceImpl(
    const std::string &root_path) {
  // TODO: Create cache manager starting at root_path/cache/network
}

Status InternalNetworkServiceImpl::RegisterHttpData(
    ServerContext *context, const proto::HttpDataRequest *httpData,
    proto::EmptyNetworkReply *reply) {
  Log::V("HttpData (id=%lld) [%s]", httpData->uid(), httpData->url().c_str());
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendChunk(ServerContext *context,
                                             const proto::ChunkRequest *chunk,
                                             proto::EmptyNetworkReply *reply) {
  Log::V("Chunk (id=%lld) [%u]", chunk->uid(),
         (uint32_t)chunk->content().length());
  return Status::OK;
}

Status InternalNetworkServiceImpl::SendHttpEvent(
    ServerContext *context, const proto::HttpEventRequest *httpEvent,
    proto::EmptyNetworkReply *reply) {
  Log::V("HttpEvent (id=%lld) [%d]", httpEvent->uid(),
         (int32_t)httpEvent->event());
  return Status::OK;
}

}  // namespace profiler
