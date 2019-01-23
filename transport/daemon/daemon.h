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
#ifndef TRANSPORT_DAEMON_H_
#define TRANSPORT_DAEMON_H_

#include <grpc++/grpc++.h>
#include <string>
#include <unordered_map>
#include <vector>
#include "commands/command.h"
#include "event_buffer.h"
#include "event_writer.h"
#include "proto/common.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "service_component.h"
#include "transport_component.h"
#include "utils/clock.h"
#include "utils/config.h"
#include "utils/file_cache.h"

namespace profiler {

class Command;

// A daemon running on the device, collecting, caching, and transporting
// profiling data. It also includes a gRPC server. The gRPC server contains a
// number of gRPC services, including 'public' ones that talk to desktop (e.g.,
// Studio) and 'internal' ones that talk to app processes.
class Daemon {
 public:
  // Creates a daemon with |clock| as the source for timing values, with
  // |config| as the configuration, |file_cache| as the manager of temporary
  // files, and |buffer| as the central storage for all generated events.
  Daemon(Clock* clock, Config* config, FileCache* file_cache,
         EventBuffer* buffer);

  // Registers |component| to the daemon, in particular, the
  // component's public and internal services to daemon's server |builder|.
  // Assumes callers are from the same thread. |component| may not be null.
  // |component| cannot be 'const &' because we need to call its non-const
  // methods that return 'Service*'.
  // Daemon takes responsibily for the lifetime of the component.
  // Note: This API is part of the legacy pipeline, new clients do not need to
  // call this API.
  void RegisterProfilerComponent(std::unique_ptr<ServiceComponent> component);

  const std::vector<std::unique_ptr<ServiceComponent>>& GetProfilerComponents()
      const {
    return profiler_components_;
  }

  std::unique_ptr<TransportComponent>& transport_component() {
    return transport_component_;
  }

  // Starts running server at |server_address| with the services that have been
  // registered.
  // Block waiting for the server to shutdown. Note that some other thread must
  // be responsible for shutting down the server for this call to ever return.
  void RunServer(const std::string& server_address);

  // This is thread safe as each command is executed exclusively.
  grpc::Status Execute(const proto::Command& command);

  // Temporary version to allow synchronous calls from the legacy API that
  // allows the callback to be executed thread safely with the command.
  grpc::Status Execute(const proto::Command& command,
                       std::function<void(void)> post);

  std::vector<proto::EventGroup> GetEventGroups(
      const proto::GetEventGroupsRequest* request);

  // Returns the clock to use across the profilers.
  Clock* clock() { return clock_; }

  // Shared cache available to all profiler services. Useful for storing data
  // which is
  // 1) large and needs to be cleaned up automatically, or
  // 2) repetitive, and you'd rather send a key to the client each time
  //    instead of the full byte string.
  FileCache* file_cache() { return file_cache_; }

  // Returns the configuration parameters.
  const Config* config() { return config_; }

  EventBuffer* buffer() { return buffer_; }

  // All current and new are written to the |writer|.
  // This call is blocking and will not return until cancel listener is called.
  void WriteEventsTo(EventWriter* writer) { buffer_->WriteEventsTo(writer); }

  // Interrupts the WriteEventsTo.
  void InterruptWriteEvents() { buffer_->InterruptWriteEvents(); }

  proto::AgentData::Status GetAgentStatus(int32_t pid);

  grpc::Status ConfigureStartupAgent(
      const profiler::proto::ConfigureStartupAgentRequest* request,
      profiler::proto::ConfigureStartupAgentResponse* response);

  // Attaches an JVMTI agent to an app. Returns true if |agent_lib_file_name| is
  // attached successfully (either an agent already exists or a new one
  // attaches), otherwise returns false.
  // Note: |agent_lib_file_name| refers to the name of the agent library file
  // located within the perfd directory, and it needs to be compatible with the
  // app's CPU architecture.
  bool TryAttachAppAgent(int32_t app_pid, const std::string& app_name,
                         const std::string& agent_lib_file_name);

  void SetHeartBeatTimestamp(int32_t app_pid, int64_t timestamp);

  const std::unordered_map<int32_t, int64_t>& heartbeat_timestamp_map() {
    return heartbeat_timestamp_map_;
  }

  std::unordered_map<int32_t, profiler::proto::AgentData::Status>&
  agent_status_map() {
    return agent_status_map_;
  }

  // Registers a function callback for a specific command.
  // Each command may only be registered once.
  void RegisterCommandHandler(
      proto::Command::CommandType type,
      const std::function<Command*(proto::Command)>& command) {
    commands_[type] = command;
  }

 private:
  // True if there is an JVMTI agent attached to an app. False otherwise.
  bool IsAppAgentAlive(int32_t app_pid, const std::string& app_name);

  // True if perfd has received a heartbeat from an app within the last
  // time interval (as specified by |GenericComponent::kHeartbeatThresholdNs|.
  // False otherwise.
  bool CheckAppHeartBeat(int32_t app_pid);

  // All command executions are guarded by this.
  std::mutex mutex_;
  // Builder of the gRPC server.
  grpc::ServerBuilder builder_;
  // Profiler components that have been registered.
  std::vector<std::unique_ptr<ServiceComponent>> profiler_components_;
  // Clock that timestamps profiling data
  Clock* clock_;
  // Config object for profiling settings
  Config* config_;
  // A shared cache for all profiler services
  FileCache* file_cache_;
  // The buffer with all the events
  EventBuffer* buffer_;
  // The unified pipeline service component.
  std::unique_ptr<TransportComponent> transport_component_;
  // Maps types to factory functions that create commands from proto objects.
  std::map<proto::Command::CommandType, std::function<Command*(proto::Command)>>
      commands_;

  // TODO (b/110830616): remove dead entries
  std::unordered_map<int32_t, int64_t> heartbeat_timestamp_map_;
  // Mapping pid -> latest status of agent (Attached / Detached).
  // TODO (b/110830616): remove dead entries
  std::unordered_map<int32_t, profiler::proto::AgentData::Status>
      agent_status_map_;
  // Mapping pid -> whether an agent is attachable.
  // TODO (b/110830616): remove dead entries
  std::unordered_map<int32_t, bool> agent_attachable_map_;
};

}  // namespace profiler

#endif  // TRANSPORT_DAEMON_H_
