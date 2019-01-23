/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "agent_service.h"

#include "daemon/daemon.h"
#include "proto/common.grpc.pb.h"

using grpc::ServerContext;
using profiler::proto::Event;
using profiler::proto::HeartBeatRequest;
using profiler::proto::HeartBeatResponse;

namespace profiler {

grpc::Status AgentServiceImpl::HeartBeat(ServerContext* context,
                                         const HeartBeatRequest* request,
                                         HeartBeatResponse* response) {
  auto now = daemon_->clock()->GetCurrentTime();
  daemon_->SetHeartBeatTimestamp(request->pid(), now);
  return grpc::Status::OK;
}

grpc::Status AgentServiceImpl::SendEvent(grpc::ServerContext* context,
                                         const proto::SendEventRequest* request,
                                         proto::EmptyResponse* response) {
  Event event;
  event.CopyFrom(request->event());
  daemon_->buffer()->Add(event);
  return grpc::Status::OK;
}

grpc::Status AgentServiceImpl::SendPayload(
    grpc::ServerContext* context, const proto::SendPayloadRequest* request,
    proto::EmptyResponse* response) {
  auto cache = daemon_->file_cache();
  cache->AddChunk(request->name(), request->payload());
  if (!request->is_partial()) {
    cache->Complete(request->name());
  }
  return grpc::Status::OK;
}

}  // namespace profiler
