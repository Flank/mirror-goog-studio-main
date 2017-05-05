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

#include "proto/agent_service.grpc.pb.h"
#include "proto/internal_event.grpc.pb.h"
#include "proto/internal_network.grpc.pb.h"

#include "memory_component.h"
#include "utils/background_queue.h"
#include "utils/clock.h"

namespace profiler {

// Function call back that returns the true/false status if the agent is
// connected to perfd. Each time the status changes this callback gets called
// with the new (current) state of the connection.
using PerfdStatusChanged = std::function<void(bool)>;

class Agent {
 public:
  enum class SocketType { kUnspecified, kAbstractSocket };

  // Should be called by everyone except JVMTI.
  // Grab the singleton instance of the Agent. This will initialize the class if
  // necessary.
  static Agent& Instance() { return Instance(SocketType::kUnspecified); }

  // Should be called only by JVMTI.
  // Temporary workaround to let JVMTI-enabled agent use Unix abstract
  // socket instead of a port number.
  // TODO: Remove this constructor after we use only JVMTI to instrument
  // bytecode on O+ devices.
  static Agent& Instance(SocketType socket_type);

  const proto::InternalEventService::Stub& event_stub() { return *event_stub_; }

  const proto::InternalNetworkService::Stub& network_stub() {
    return *network_stub_;
  }

  MemoryComponent* memory_component() { return memory_component_; }

  void AddPerfdStatusChangedCallback(PerfdStatusChanged callback);

  BackgroundQueue* background_queue() { return &background_queue_; }

 private:
  static constexpr int64_t kHeartBeatIntervalNs = Clock::ms_to_ns(250);

  // Use Agent::Instance() to initialize.
  explicit Agent(SocketType socket_type);
  ~Agent() = delete;  // TODO: Support destroying the agent

  std::unique_ptr<proto::AgentService::Stub> service_stub_;
  std::unique_ptr<proto::InternalEventService::Stub> event_stub_;
  std::unique_ptr<proto::InternalNetworkService::Stub> network_stub_;

  std::mutex callback_mutex_;
  std::list<PerfdStatusChanged> perfd_status_changed_callbacks_;

  MemoryComponent* memory_component_;

  std::thread heartbeat_thread_;

  BackgroundQueue background_queue_;

  /**
   * A thread that is used to continuously ping perfd at regular intervals
   * as a signal that perfa is alive. This signal is used by the Studio
   * Profilers to determine whether certain advanced profiling features
   * should be enabled.
   */
  void RunHeartbeatThread();
};

}  // end of namespace profiler

#endif  // AGENT_AGENT_H_
