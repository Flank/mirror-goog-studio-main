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
#include <cassert>

namespace profiler {

using grpc::ServerContext;
using grpc::Status;

grpc::Status InternalMemoryServiceImpl::RegisterMemoryAgent(
    grpc::ServerContext *context,
    const proto::RegisterMemoryAgentRequest *request,
    grpc::ServerWriter<proto::MemoryControlRequest> *writer) {
  int32_t pid = request->pid();
  {
    std::lock_guard<std::mutex> request_guard(status_mutex_);
    // TODO: set to false when perfa dies (e.g. no more heartbeat)
    app_control_stream_statuses_[pid] = true;
  }

  // TODO: this grpc does not return which essentially consumes
  // a thread permenantly within the server's thread pool. If we
  // happen to be profiling many apps simultaneously this would be
  // a problem. Investigate proper solution for this (other grpc
  // configurations?)
  std::unique_lock<std::mutex> lock(control_mutex_);
  while (true) {
    // Blocks and proceeds only when there is a control request
    // directed at the particular app that started this stream.
    auto it = pending_control_requests_.find(pid);
    while (it == pending_control_requests_.end()) {
      control_cv_.wait(lock);
      it = pending_control_requests_.find(pid);
    }

    writer->Write(it->second);
    pending_control_requests_.erase(pid);
    control_cv_.notify_all();
  }
  assert(!"Unreachable");

  return grpc::Status::OK;
}

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

grpc::Status InternalMemoryServiceImpl::RecordAllocationEvents(
    grpc::ServerContext *context,
    const proto::RecordAllocationEventsRequest *request,
    proto::EmptyMemoryReply *reply) {
  auto result = collectors_.find(request->process_id());
  if (result == collectors_.end()) {
    return ::grpc::Status(
        ::grpc::StatusCode::NOT_FOUND,
        "The memory collector for the specified pid has not been started yet.");
  }

  return Status::OK;
}

bool InternalMemoryServiceImpl::SendRequestToAgent(
    const proto::MemoryControlRequest &request) {
  // Protect this method from being called from multiple threads,
  // as we need to avoid overwriting a pending signal before the
  // the control stream has a chance to consume it.
  // Revisit if we need to send a lot of high frequency signal
  // in which case we can switch to a queue.
  std::lock_guard<std::mutex> request_guard(status_mutex_);

  int32_t pid = request.pid();
  if (!app_control_stream_statuses_[pid]) {
    return false;
  }

  {
    std::unique_lock<std::mutex> lock(control_mutex_);
    assert(pending_control_requests_.find(pid) ==
           pending_control_requests_.end());
    pending_control_requests_[pid] = request;
    control_cv_.notify_all();

    // Blocks until the corresponding control stream has sent
    // the signal off to an app (by waiting on the request in
    // the map to be erased.)
    while (pending_control_requests_.find(pid) !=
           pending_control_requests_.end()) {
      control_cv_.wait(lock);
    }
  }

  return true;
}

}  // namespace profiler
