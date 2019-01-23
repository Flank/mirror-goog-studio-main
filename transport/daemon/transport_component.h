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
#ifndef DAEMON_TRANSPORT_COMPONENT_H_
#define DAEMON_TRANSPORT_COMPONENT_H_

#include <atomic>
#include <thread>
#include <unordered_map>

#include "daemon/agent_service.h"
#include "daemon/service_component.h"
#include "daemon/transport_service.h"

namespace profiler {

class Daemon;
using AgentStatusChanged = std::function<void(int processId)>;

class TransportComponent final : public ServiceComponent {
 public:
  static constexpr int64_t kHeartbeatThresholdNs = Clock::ms_to_ns(500);

  explicit TransportComponent(Daemon* daemon);
  ~TransportComponent() override;

  // Returns the service that talks to desktop clients (e.g., Studio).
  grpc::Service* GetPublicService() override { return &public_service_; }

  // Returns the service that talks to device clients (e.g., the agent).
  grpc::Service* GetInternalService() override { return &agent_service_; }

  void AddAgentStatusChangedCallback(AgentStatusChanged callback) {
    agent_status_changed_callbacks_.push_back(callback);
  }

  // Forwards a command from the daemon to the agent.
  bool ForwardCommandToAgent(const proto::Command& command) {
    return agent_service_.SendCommandToAgent(command);
  }

 private:
  void RunAgentStatusThread();

  Daemon* daemon_;
  TransportServiceImpl public_service_;
  AgentServiceImpl agent_service_;

  std::list<AgentStatusChanged> agent_status_changed_callbacks_;

  std::atomic_bool is_running_{true};
  std::thread status_thread_;
};

}  // namespace profiler

#endif  // DAEMON_TRANSPORT_COMPONENT_H_
