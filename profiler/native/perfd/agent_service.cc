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
#include "agent_service.h"

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
  // Ignore data if no sessions associated with the pid is alive.
  auto session = daemon_->sessions()->GetLastSession();
  if (session->IsActive() && session->info().pid() == request->pid()) {
    Event event;
    event.CopyFrom(request->event());
    event.set_session_id(session->info().session_id());
    daemon_->buffer()->Add(event);
  }

  return grpc::Status::OK;
}

}  // namespace profiler
