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

#include "utils/log.h"
#include "utils/thread_name.h"

namespace profiler {

using proto::InternalMemoryService;
using proto::MemoryControlRequest;
using proto::RegisterMemoryAgentRequest;

MemoryComponent::MemoryComponent(std::shared_ptr<grpc::Channel> channel)
    : is_control_stream_started_(false) {
  service_stub_ = InternalMemoryService::NewStub(channel);
}

void MemoryComponent::OpenControlStream() {
  if (is_control_stream_started_) {
    return;
  }

  RegisterMemoryAgentRequest memory_agent_request;
  memory_agent_request.set_pid(getpid());
  memory_control_stream_ = service_stub_->RegisterMemoryAgent(
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
