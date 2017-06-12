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

#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>

#include <grpc++/grpc++.h>

#include "proto/internal_memory.grpc.pb.h"

#include "utils/background_queue.h"

namespace profiler {

using grpc::Status;
using proto::MemoryControlRequest;
using proto::InternalMemoryService;
using proto::RegisterMemoryAgentRequest;

using MemoryControlHandler = std::function<void(const MemoryControlRequest*)>;

// Function for submitting a memory grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using MemoryServiceTask = std::function<Status(
    proto::InternalMemoryService::Stub& stub, grpc::ClientContext& context)>;

// A profiler component in the agent that is responsible for handling
// memory-specific communications between perfa and perfd.
class MemoryComponent {
 public:
  explicit MemoryComponent(BackgroundQueue* background_queue,
                           bool can_grpc_target_change);

  void Connect(std::shared_ptr<grpc::Channel> channel);

  // Open the streaming grpc call to perfd.
  void OpenControlStream();

  void RegisterMemoryControlHandler(MemoryControlHandler handler);

  void SubmitMemoryTasks(const std::vector<MemoryServiceTask>& tasks);

 private:
  ~MemoryComponent() = delete;

  InternalMemoryService::Stub& service_stub();

  /**
   * A thread that is used to accept data from a streaming grpc call from perfd
   * and forward control signals that are directed to the perfa memory agent.
   */
  void RunMemoryControlThread();

  bool is_control_stream_started_;
  bool can_grpc_target_change_;
  bool grpc_target_initialized_;
  BackgroundQueue* background_queue_;

  std::mutex connect_mutex_;
  std::condition_variable connect_cv_;
  std::unique_ptr<proto::InternalMemoryService::Stub> service_stub_;
  std::list<MemoryControlHandler> memory_control_handlers_;
  std::thread memory_control_thread_;
  grpc::ClientContext memory_control_context_;
  std::unique_ptr<grpc::ClientReader<proto::MemoryControlRequest>>
      memory_control_stream_;
};

}  // end of namespace profiler

#endif  // AGENT_MEMORY_COMPONENT_H_
