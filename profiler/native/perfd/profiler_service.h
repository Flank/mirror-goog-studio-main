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
#ifndef PERFD_PROFILER_SERVICE_H_
#define PERFD_PROFILER_SERVICE_H_

#include <grpc++/grpc++.h>

#include "perfd/daemon.h"
#include "proto/profiler.grpc.pb.h"

#include <unordered_map>

namespace profiler {

class ProfilerServiceImpl final
    : public profiler::proto::ProfilerService::Service {
 public:
  explicit ProfilerServiceImpl(
      Daemon* daemon,
      std::unordered_map<int32_t, profiler::proto::AgentStatusResponse::Status>*
          agent_status_map)
      : daemon_(daemon), agent_status_map_(*agent_status_map) {}

  grpc::Status GetCurrentTime(grpc::ServerContext* context,
                              const profiler::proto::TimeRequest* request,
                              profiler::proto::TimeResponse* response) override;

  grpc::Status GetVersion(grpc::ServerContext* context,
                          const profiler::proto::VersionRequest* request,
                          profiler::proto::VersionResponse* response) override;

  grpc::Status GetBytes(grpc::ServerContext* context,
                        const profiler::proto::BytesRequest* request,
                        profiler::proto::BytesResponse* response) override;

  grpc::Status GetAgentStatus(
      grpc::ServerContext* context,
      const profiler::proto::AgentStatusRequest* request,
      profiler::proto::AgentStatusResponse* response) override;

  grpc::Status GetDevices(
      grpc::ServerContext* context,
      const profiler::proto::GetDevicesRequest* request,
      profiler::proto::GetDevicesResponse* response) override;

  grpc::Status ConfigureStartupAgent(
      grpc::ServerContext* context,
      const profiler::proto::ConfigureStartupAgentRequest* request,
      profiler::proto::ConfigureStartupAgentResponse* response) override;

  grpc::Status BeginSession(
      grpc::ServerContext* context,
      const profiler::proto::BeginSessionRequest* request,
      profiler::proto::BeginSessionResponse* response) override;

  grpc::Status EndSession(
      grpc::ServerContext* context,
      const profiler::proto::EndSessionRequest* request,
      profiler::proto::EndSessionResponse* response) override;

  grpc::Status GetSession(
      grpc::ServerContext* context,
      const profiler::proto::GetSessionRequest* request,
      profiler::proto::GetSessionResponse* response) override;

  grpc::Status GetSessions(
      grpc::ServerContext* context,
      const profiler::proto::GetSessionsRequest* request,
      profiler::proto::GetSessionsResponse* response) override;

 private:
  // Attaches an JVMTI agent to an app. Returns true if |agent_lib_file_name| is
  // attached successfully (either an agent already exists or a new one
  // attaches), otherwise returns false.
  // Note: |agent_lib_file_name| refers to the name of the agent library file
  // located within the perfd directory, and it needs to be compatible with the
  // app's CPU architecture.
  bool TryAttachAppAgent(int32_t app_pid, const std::string& app_name,
                         const std::string& agent_lib_file_name);

  // True if there is an JVMTI agent attached to an app. False otherwise.
  bool IsAppAgentAlive(int32_t app_pid, const std::string& app_name);

  // True if perfd has received a heartbeat from an app within the last
  // time interval (as specified by |GenericComponent::kHeartbeatThresholdNs|.
  // False otherwise.
  bool CheckAppHeartBeat(int32_t app_pid);

  // The daemon this service talks to.
  Daemon* daemon_;

  std::unordered_map<int32_t, profiler::proto::AgentStatusResponse::Status>&
      agent_status_map_;
};

}  // namespace profiler

#endif  // PERFD_PROFILER_SERVICE_H_
