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

#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include <grpc++/grpc++.h>

#include "proto/agent_service.grpc.pb.h"
#include "proto/internal_event.grpc.pb.h"
#include "proto/internal_network.grpc.pb.h"

#include "memory_component.h"
#include "utils/background_queue.h"
#include "utils/clock.h"
#include "utils/config.h"

namespace profiler {

// Function call back that returns the true/false status if the agent is
// connected to perfd. Each time the status changes this callback gets called
// with the new (current) state of the connection.
using PerfdStatusChanged = std::function<void(bool)>;

// Function for submitting a network grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using NetworkServiceTask = std::function<grpc::Status(
    proto::InternalNetworkService::Stub& stub, grpc::ClientContext& context)>;

// Function for submitting an event grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using EventServiceTask = std::function<grpc::Status(
    proto::InternalEventService::Stub& stub, grpc::ClientContext& context)>;

// Function for submitting an agent grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using AgentServiceTask = std::function<grpc::Status(
    proto::AgentService::Stub& stub, grpc::ClientContext& context)>;

class Agent {
 public:
  // Should be called to obtain the instance after it is initialized by Instance
  // with a valid config.
  // Grab the singleton instance of the Agent. This will initialize the class if
  // necessary.
  static Agent& Instance() { return Instance(nullptr); }

  // Returns a singleton instance of the agent.
  // The first call, assumes |config| is a valid profiler::Config object.
  // All following calls, simply return the singleton instance regardless of the
  // value of |config|.
  static Agent& Instance(const profiler::Config* config);

  // In O+, this method will block until the Agent is connected to Perfd for the
  // very first time (e.g. when Perfd sends the client socket fd for the agent
  // to connect to). If/when perfd dies, the memory service stub inside can also
  // point to a previous, stale Perfd. However, when the Agent reconnects to a
  // new Perfd, the stub will resolve to the correct grpc target.
  MemoryComponent& memory_component();

  void SubmitNetworkTasks(const std::vector<NetworkServiceTask>& tasks);

  void SubmitEventTasks(const std::vector<EventServiceTask>& tasks);

  void AddPerfdStatusChangedCallback(PerfdStatusChanged callback);

 private:
  static constexpr int64_t kHeartBeatIntervalNs = Clock::ms_to_ns(250);

  // Use Agent::Instance() to initialize.
  // |config| is a valid profiler::Config object.
  explicit Agent(const Config& config);
  ~Agent() = delete;  // TODO: Support destroying the agent

  // In O+, getting the service stubs below will block until the Agent is
  // connected to Perfd for the very first time (e.g. when Perfd sends the
  // client socket fd for the agent to connect to). If/when perfd dies, the
  // stubs can also point to a previous, stale Perfd. If/when a new Perfd
  // isntance sends a new client socket fd to the Agent, the stubs will be
  // resolved to the correct grpc target.
  proto::AgentService::Stub& agent_stub();
  proto::InternalEventService::Stub& event_stub();
  proto::InternalNetworkService::Stub& network_stub();

  /**
   * Connects/reconnects to perfd via the provided target.
   */
  void ConnectToPerfd(const std::string& target);

  /**
   * A thread that is used to continuously ping perfd at regular intervals
   * as a signal that perfa is alive. This signal is used by the Studio
   * Profilers to determine whether certain advanced profiling features
   * should be enabled.
   */
  void RunHeartbeatThread();

  /**
   * A thread that opens a socket for perfd to communicate to. The address of
   * the socket is defined as: |kAgentSocketName| + app's process id - this is
   * to ensure that multiple applcations being profiled each opens a unique
   * socket. Each connection is meant to be short-lived and sends only one
   * message at a time after which the socket connection will be closed.
   */
  void RunSocketThread();

  // Used for |connect_cv_| and protects |agent_stub_|, |event_stub_|,
  // |network_stub_| and |memory_component_|
  std::mutex connect_mutex_;
  std::condition_variable connect_cv_;
  // The current grpc target we are currently connected to. We only
  // reinstantiate channel if the target has changed. Otherwise in the case of
  // O+ unix socket, if the file descriptor happens to be the same, re-creating
  // the channel on the same fd can cause the socket to be closed immediately
  // (TODO: investigate further).
  // For pre-O, the target is an ip address. (e.g. |profiler::kServerAddress|)
  // For O+ the target is of the form "unix:&fd", where fd maps to the client
  // socket used for communicating with the perfd server.
  std::string current_connected_target_;
  std::shared_ptr<grpc::Channel> channel_;
  std::unique_ptr<proto::AgentService::Stub> agent_stub_;
  std::unique_ptr<proto::InternalEventService::Stub> event_stub_;
  std::unique_ptr<proto::InternalNetworkService::Stub> network_stub_;
  MemoryComponent* memory_component_;

  // Protects |perfd_status_changed_callbacks_|
  std::mutex callback_mutex_;
  std::list<PerfdStatusChanged> perfd_status_changed_callbacks_;

  // Used for |RunHeartbeatThread|
  std::thread heartbeat_thread_;
  // O+ only - Used for |RunSocketThread|
  std::thread socket_thread_;
  BackgroundQueue background_queue_;

  // Whether the agent and its children service stub should anticipate
  // the underlying channel to perfd changing.
  // This value should only be true for O+ with JVMTI.
  bool can_grpc_target_change_;

  // Whether the agent has been connected to a grpc target. Before the
  // first time the agent connects to a perfd instance, this would be
  // false and any service stubs are expected to be |nullptr|.
  bool grpc_target_initialized_;
};

}  // end of namespace profiler

#endif  // AGENT_AGENT_H_
