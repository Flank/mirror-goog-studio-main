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
#ifndef AGENT_AGENT_H_
#define AGENT_AGENT_H_

#include <memory>
#include <mutex>
#include <thread>

#include <grpc++/grpc++.h>

#include "proto/internal_event.grpc.pb.h"
#include "proto/internal_memory.grpc.pb.h"
#include "proto/internal_network.grpc.pb.h"
#include "proto/agent_service.grpc.pb.h"

#include "utils/background_queue.h"
#include "utils/clock.h"

namespace profiler {

// Function call back that returns the true/false status if the agent is connected
// to perfd. Each time the status changes this callback gets called with the new
// (current) state of the connection.
using PerfdStatusChanged = std::function<void(bool)>;

class Agent {
 public:
  // Grab the singleton instance of the Agent. This will initialize the class if
  // necessary.
  static Agent& Instance();

  const proto::InternalEventService::Stub& event_stub() { return *event_stub_; }

  const proto::InternalMemoryService::Stub& memory_stub() {
    return *memory_stub_;
  }

  const proto::InternalNetworkService::Stub& network_stub() {
    return *network_stub_;
  }

  void AddPerfdStatusChangedCallback(PerfdStatusChanged callback);

  BackgroundQueue* background_queue() { return &background_queue_; }

 private:
  static constexpr int64_t kHeartBeatIntervalNs = Clock::ms_to_ns(250);

  // Use Agent::Instance() to initialize.
  explicit Agent(const char* address);
  ~Agent() = delete;  // TODO: Support destroying the agent

  std::unique_ptr<proto::AgentService::Stub> service_stub_;
  std::unique_ptr<proto::InternalEventService::Stub> event_stub_;
  std::unique_ptr<proto::InternalMemoryService::Stub> memory_stub_;
  std::unique_ptr<proto::InternalNetworkService::Stub> network_stub_;

  std::mutex callback_mutex_;
  std::list<PerfdStatusChanged> perfd_status_changed_callbacks_;

  std::thread heartbeat_thread_;

  BackgroundQueue background_queue_;

  void RunHeartbeatThread();
};

}  // end of namespace profiler

#endif  // AGENT_AGENT_H_
