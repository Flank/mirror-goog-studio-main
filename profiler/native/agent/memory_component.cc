/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "memory_component.h"

#include <sys/types.h>
#include <unistd.h>

#include "utils/config.h"
#include "utils/log.h"
#include "utils/thread_name.h"

namespace profiler {

MemoryComponent::MemoryComponent(BackgroundQueue* background_queue,
                                 bool can_grpc_target_change)
    : is_control_stream_started_(false),
      can_grpc_target_change_(can_grpc_target_change),
      grpc_target_initialized_(false),
      background_queue_(background_queue) {}

void MemoryComponent::Connect(std::shared_ptr<grpc::Channel> channel) {
  std::lock_guard<std::mutex> guard(connect_mutex_);
  // TODO: re-establish control stream if it has already started from a previous
  // connection.
  service_stub_ = InternalMemoryService::NewStub(channel);

  if (!grpc_target_initialized_) {
    grpc_target_initialized_ = true;
    connect_cv_.notify_all();
  }
}

proto::InternalMemoryService::Stub& MemoryComponent::service_stub() {
  std::unique_lock<std::mutex> lock(connect_mutex_);
  while (!grpc_target_initialized_ || service_stub_.get() == nullptr) {
    connect_cv_.wait(lock);
  }

  return *(service_stub_.get());
}

void MemoryComponent::OpenControlStream() {
  if (is_control_stream_started_) {
    return;
  }

  RegisterMemoryAgentRequest memory_agent_request;
  memory_agent_request.set_pid(getpid());
  memory_control_stream_ = service_stub().RegisterMemoryAgent(
      &memory_control_context_, memory_agent_request);
  memory_control_thread_ =
      std::thread(&MemoryComponent::RunMemoryControlThread, this);

  is_control_stream_started_ = true;
  Log::V("Memory control stream started.");
}

void MemoryComponent::RegisterMemoryControlHandler(
    MemoryControlHandler handler) {
  memory_control_handlers_.push_back(handler);
}

void MemoryComponent::SubmitMemoryTasks(
    const std::vector<MemoryServiceTask>& tasks) {
  background_queue_->EnqueueTask([this, tasks] {
    for (auto task : tasks) {
      if (can_grpc_target_change_) {
        bool success = false;
        do {
          // Each grpc call needs a new ClientContext.
          grpc::ClientContext ctx;
          Config::SetClientContextTimeout(&ctx, kGrpcTimeoutSec);
          Status status = task(service_stub(), ctx);
          success = status.ok();
        } while (!success);
      } else {
        grpc::ClientContext ctx;
        task(service_stub(), ctx);
      }
    }
  });
}

void MemoryComponent::RunMemoryControlThread() {
  SetThreadName("Studio:MemoryAgent");
  MemoryControlRequest request;
  while (memory_control_stream_->Read(&request)) {
    for (auto handler : memory_control_handlers_) {
      handler(&request);
    }
  }
}

}  // namespace profiler
