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
#ifndef AGENT_MEMORY_COMPONENT_H_
#define AGENT_MEMORY_COMPONENT_H_

#include <memory>
#include <thread>

#include <grpc++/grpc++.h>

#include "proto/internal_memory.grpc.pb.h"

namespace profiler {

using proto::MemoryControlRequest;
using MemoryControlHandler = std::function<void(const MemoryControlRequest*)>;

// A profiler component in the agent that is responsible for handling
// memory-specific communications between perfa and perfd.
class MemoryComponent {
 public:
  explicit MemoryComponent(std::shared_ptr<grpc::Channel> channel);

  const proto::InternalMemoryService::Stub& service_stub() {
    return *service_stub_;
  }

  // Open the streaming grpc call to perfd.
  void OpenControlStream();

  void RegisterMemoryControlHandler(MemoryControlHandler handler);

 private:
  ~MemoryComponent() = delete;

  /**
   * A thread that is used to accept data from a streaming grpc call from perfd
   * and forward control signals that are directed to the perfa memory agent.
   */
  void RunMemoryControlThread();

  bool is_control_stream_started_;
  std::unique_ptr<proto::InternalMemoryService::Stub> service_stub_;
  std::list<MemoryControlHandler> memory_control_handlers_;
  std::thread memory_control_thread_;
  grpc::ClientContext memory_control_context_;
  std::unique_ptr<grpc::ClientReader<proto::MemoryControlRequest>>
      memory_control_stream_;
};

}  // end of namespace profiler

#endif  // AGENT_MEMORY_COMPONENT_H_
