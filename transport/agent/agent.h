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
#include "proto/internal_cpu.grpc.pb.h"
#include "proto/internal_energy.grpc.pb.h"
#include "proto/internal_event.grpc.pb.h"
#include "proto/internal_network.grpc.pb.h"

#include "memory_component.h"
#include "utils/agent_task.h"
#include "utils/background_queue.h"
#include "utils/clock.h"

namespace profiler {

// Function call back that returns the true/false status if the agent is
// connected to daemon. Each time the status changes this callback gets called
// with the new (current) state of the connection.
using DaemonStatusChanged = std::function<void(bool)>;

// Function that handles a Command forwarded from daemon.
using CommandHandler = std::function<void(const proto::Command*)>;

// Function for submitting a network grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using NetworkServiceTask = std::function<grpc::Status(
    proto::InternalNetworkService::Stub& stub, grpc::ClientContext& context)>;

// Function for submitting an event grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using EventServiceTask = std::function<grpc::Status(
    proto::InternalEventService::Stub& stub, grpc::ClientContext& context)>;

// Function for submitting an energy grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using EnergyServiceTask = std::function<grpc::Status(
    proto::InternalEnergyService::Stub& stub, grpc::ClientContext& context)>;

// Function for submitting a CPU grpc request via |stub| using the given
// |context|. Returns the status from the grpc call.
using CpuServiceTask = std::function<grpc::Status(
    proto::InternalCpuService::Stub& stub, grpc::ClientContext& context)>;

class Agent {
 public:
  // Should be called to obtain the instance after it is initialized by Instance
  // with a valid config.
  // Grab the singleton instance of the Agent. This will initialize the class if
  // necessary.
  static Agent& Instance() {
    return Instance(proto::AgentConfig::default_instance());
  }

  // Returns a singleton instance of the agent.
  // The first call, assumes |config| is a valid proto::Config object.
  // All following calls, simply return the singleton instance regardless of the
  // value of |config|.
  static Agent& Instance(const proto::AgentConfig& config);

  const proto::AgentConfig& agent_config() const { return agent_config_; }

  // In O+, this method will block until the Agent is connected to Daemon for
  // the very first time (e.g. when daemon sends the client socket fd for the
  // agent to connect to). If/when daemon dies, the memory service stub inside
  // can also point to a previous, stale daemon. However, when the Agent
  // reconnects to a new daemon, the stub will resolve to the correct grpc
  // target.
  MemoryComponent& wait_and_get_memory_component();

  // Tell the agent to start sending heartbeats back to daemon to signal
  // that the app agent is alive.
  void StartHeartbeat();

  void SubmitAgentTasks(const std::vector<AgentServiceTask>& tasks);

  void SubmitNetworkTasks(const std::vector<NetworkServiceTask>& tasks);

  void SubmitEventTasks(const std::vector<EventServiceTask>& tasks);

  void SubmitEnergyTasks(const std::vector<EnergyServiceTask>& tasks);

  void SubmitCpuTasks(const std::vector<CpuServiceTask>& tasks);

  void AddDaemonStatusChangedCallback(DaemonStatusChanged callback);

  // Callback for everytime daemon is reconnected to agent.
  // (e.g. Studio restarts within the duration of the same app instance)
  void AddDaemonConnectedCallback(std::function<void()> callback);

  // Initializes profiler-specific componenets.
  void InitializeProfilers();

  bool IsProfilerInitalized();

  // Registers a handler for the given command type.
  // Newer registration overwrites prior registrations of the same type.
  void RegisterCommandHandler(proto::Command::CommandType type,
                              const CommandHandler& handler) {
    command_handlers_[type] = handler;
  }

 private:
  static constexpr int64_t kHeartBeatIntervalNs = Clock::ms_to_ns(250);

  // Use Agent::Instance() to initialize.
  // |config| is a valid profiler::Config object.
  explicit Agent(const proto::AgentConfig& config);
  ~Agent() = delete;  // TODO: Support destroying the agent

  // In O+, getting the service stubs below will block until the Agent is
  // connected to daemon for the very first time (e.g. when daemon sends the
  // client socket fd for the agent to connect to). If/when daemon dies, the
  // stubs can also point to a previous, stale daemon. If/when a new daemon
  // isntance sends a new client socket fd to the Agent, the stubs will be
  // resolved to the correct grpc target.
  proto::AgentService::Stub& agent_stub();
  proto::InternalCpuService::Stub& cpu_stub();
  proto::InternalEnergyService::Stub& energy_stub();
  proto::InternalEventService::Stub& event_stub();
  proto::InternalNetworkService::Stub& network_stub();

  /**
   * Connects/reconnects to transport daemon via the provided target.
   */
  void ConnectToDaemon(const std::string& target);

  /**
   * A thread that is used to continuously ping daemon at regular intervals
   * as a signal that perfa is alive. This signal is used by the Studio
   * Profilers to determine whether certain advanced profiling features
   * should be enabled.
   */
  void RunHeartbeatThread();

  /**
   * A thread that opens a socket for daemon to communicate to. The address of
   * the socket is defined as: |kAgentSocketName| + app's process id - this is
   * to ensure that multiple applcations being profiled each opens a unique
   * socket. Each connection is meant to be short-lived and sends only one
   * message at a time after which the socket connection will be closed.
   */
  void RunSocketThread();

  /**
   * A thread that handles Commands forwarded from daemon.
   */
  void RunCommandHandlerThread();

  /**
   * Opens the grpc call to stream commands from the daemon.
   */
  void OpenCommandStream();

  proto::AgentConfig agent_config_;

  // Used for |connect_cv_| and protects |agent_stub_|, |event_stub_|,
  // |io_stub_|, |network_stub_| and |wait_and_get_memory_component_|
  std::mutex connect_mutex_;
  std::condition_variable connect_cv_;
  // The current grpc target we are currently connected to. We only
  // reinstantiate channel if the target has changed. Otherwise in the case of
  // O+ unix socket, if the file descriptor happens to be the same, re-creating
  // the channel on the same fd can cause the socket to be closed immediately
  // (TODO: investigate further).
  // For pre-O, the target is an ip address. (e.g. |profiler::kServerAddress|)
  // For O+ the target is of the form "unix:&fd", where fd maps to the client
  // socket used for communicating with the daemon server.
  std::string current_connected_target_;
  std::shared_ptr<grpc::Channel> channel_;
  std::unique_ptr<proto::AgentService::Stub> agent_stub_;
  std::unique_ptr<proto::InternalCpuService::Stub> cpu_stub_;
  std::unique_ptr<proto::InternalEnergyService::Stub> energy_stub_;
  std::unique_ptr<proto::InternalEventService::Stub> event_stub_;
  std::unique_ptr<proto::InternalNetworkService::Stub> network_stub_;
  std::unique_ptr<grpc::ClientReader<proto::Command>> command_stream_reader_;
  std::unique_ptr<grpc::ClientContext> command_stream_context_;

  // Protects |daemon_status_changed_callbacks_|
  std::mutex callback_mutex_;
  std::list<DaemonStatusChanged> daemon_status_changed_callbacks_;

  std::mutex daemon_connected_mutex_;
  std::vector<std::function<void()>> daemon_connected_callbacks_;

  // Maps command types to functions that handle command proto data.
  std::map<proto::Command::CommandType, CommandHandler> command_handlers_;

  // Used for |RunHeartbeatThread|
  std::thread heartbeat_thread_;
  // O+ only - Used for |RunSocketThread|
  std::thread socket_thread_;
  // Used for |RunCommandHandlerThread|
  std::thread command_handler_thread_;

  BackgroundQueue background_queue_;

  // Whether the agent and its children service stub should anticipate
  // the underlying channel to daemon changing.
  // This value should only be true for O+ with JVMTI.
  bool can_grpc_target_change_;

  // Whether the agent has been connected to a grpc target. Before the
  // first time the agent connects to a daemon instance, this would be
  // false and any service stubs are expected to be |nullptr|.
  bool grpc_target_initialized_;

  MemoryComponent* memory_component_;
  // Whether profiler componets (e.g. byte-code instrumentation) are
  // initialized. Set to true whenever a profling session starts.
  bool profiler_initialized_;
  std::mutex profiler_mutex_;
  std::condition_variable profiler_cv_;
};

}  // end of namespace profiler

#endif  // AGENT_AGENT_H_
