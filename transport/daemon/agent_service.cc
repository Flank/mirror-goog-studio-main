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

#include <unistd.h>
#include <cassert>

#include "daemon/daemon.h"
#include "proto/common.grpc.pb.h"

using grpc::ServerContext;
using profiler::proto::EmptyResponse;
using profiler::proto::Event;
using profiler::proto::HeartBeatRequest;
using profiler::proto::SendCommandRequest;

namespace profiler {

grpc::Status AgentServiceImpl::HeartBeat(ServerContext* context,
                                         const HeartBeatRequest* request,
                                         EmptyResponse* response) {
  auto now = daemon_->clock()->GetCurrentTime();
  daemon_->SetHeartBeatTimestamp(request->pid(), now);
  return grpc::Status::OK;
}

grpc::Status AgentServiceImpl::SendCommand(
    grpc::ServerContext* context, const proto::SendCommandRequest* request,
    proto::EmptyResponse* response) {
  return daemon_->Execute(request->command());
}

grpc::Status AgentServiceImpl::SendEvent(grpc::ServerContext* context,
                                         const proto::SendEventRequest* request,
                                         proto::EmptyResponse* response) {
  Event event;
  event.CopyFrom(request->event());
  daemon_->buffer()->Add(event);
  return grpc::Status::OK;
}

grpc::Status AgentServiceImpl::SendBytes(grpc::ServerContext* context,
                                         const proto::SendBytesRequest* request,
                                         proto::EmptyResponse* response) {
  auto cache = daemon_->file_cache();
  cache->AddChunk(request->name(), request->bytes());
  if (!request->is_partial()) {
    cache->Complete(request->name());
  }
  return grpc::Status::OK;
}

grpc::Status AgentServiceImpl::RegisterAgent(
    grpc::ServerContext* context, const proto::RegisterAgentRequest* request,
    grpc::ServerWriter<proto::Command>* writer) {
  int32_t pid = request->pid();
  {
    std::lock_guard<std::mutex> request_guard(status_mutex_);
    // TODO: set to false when agent dies (e.g. no more heartbeat)
    app_command_stream_statuses_[pid] = true;
  }

  // TODO: this grpc does not return which essentially consumes
  // a thread permenantly within the server's thread pool. If we
  // happen to be profiling many apps simultaneously this would be
  // a problem. Investigate proper solution for this (other grpc
  // configurations?)
  std::unique_lock<std::mutex> lock(command_mutex_);
  while (true) {
    // Blocks and proceeds only when there is a command request
    // directed at the particular app that started this stream.
    auto it = pending_commands_.find(pid);
    while (it == pending_commands_.end()) {
      command_cv_.wait(lock);
      it = pending_commands_.find(pid);
    }

    writer->Write(it->second);
    pending_commands_.erase(pid);
    command_cv_.notify_all();
  }
  assert(!"Unreachable");

  return grpc::Status::OK;
}

bool AgentServiceImpl::SendCommandToAgent(const proto::Command& command) {
  // Protect this method from being called from multiple threads,
  // as we need to avoid overwriting a pending signal before the
  // the control stream has a chance to consume it.
  // Revisit if we need to send a lot of high frequency signal
  // in which case we can switch to a queue.
  std::lock_guard<std::mutex> command_guard(status_mutex_);

  int32_t pid = command.pid();
  if (!app_command_stream_statuses_[pid]) {
    return false;
  }

  {
    std::unique_lock<std::mutex> lock(command_mutex_);
    assert(pending_commands_.find(pid) == pending_commands_.end());
    pending_commands_[pid] = command;
    command_cv_.notify_all();

    // Blocks until the corresponding control stream has sent
    // the signal off to an app (by waiting on the command in
    // the map to be erased.)
    // TODO: possible deadlock. Protect |pending_commands_|.
    while (pending_commands_.find(pid) != pending_commands_.end()) {
      command_cv_.wait(lock);
    }
  }

  return true;
}

}  // namespace profiler
