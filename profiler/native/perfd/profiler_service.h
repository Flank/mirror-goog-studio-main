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
#include "perfd/sessions/sessions_manager.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/clock.h"
#include "utils/file_cache.h"

#include <unordered_map>

namespace profiler {

class ProfilerServiceImpl final
    : public profiler::proto::ProfilerService::Service {
 public:
  explicit ProfilerServiceImpl(
      Daemon::Utilities* utilities,
      std::unordered_map<int32_t, int64_t>* heartbeat_timestamp_map)
      : clock_(utilities->clock()),
        config_(utilities->config()),
        file_cache_(*utilities->file_cache()),
        sessions_(clock_),
        heartbeat_timestamp_map_(*heartbeat_timestamp_map) {}

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

  // The current implementation only returns 'connected processes' and is for
  // testing only. When we move process discover to perfd, we will fix this.
  // TODO: If needed, add a flag to indicate a process is 'connected'.
  grpc::Status GetProcesses(
      grpc::ServerContext* context,
      const profiler::proto::GetProcessesRequest* request,
      profiler::proto::GetProcessesResponse* response) override;

  grpc::Status GetDevices(
      grpc::ServerContext* context,
      const profiler::proto::GetDevicesRequest* request,
      profiler::proto::GetDevicesResponse* response) override;

  grpc::Status AttachAgent(
      grpc::ServerContext* context,
      const profiler::proto::AgentAttachRequest* request,
      profiler::proto::AgentAttachResponse* response) override;

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

  grpc::Status DeleteSession(
      grpc::ServerContext* context,
      const profiler::proto::DeleteSessionRequest* request,
      profiler::proto::DeleteSessionResponse* response) override;

 private:
  // True if an JVMTI agent has been attached to an app. False otherwise.
  bool IsAppAgentAlive(int app_pid, const char* app_name);
  // True if perfd has received a heartbeat from an app within the last
  // time interval (as specified by |GenericComponent::kHeartbeatThresholdNs|.
  // False otherwise.
  bool CheckAppHeartBeat(int app_pid);

  // Clock knows about timestamps.
  const Clock& clock_;
  const Config& config_;
  FileCache& file_cache_;
  SessionsManager sessions_;
  std::unordered_map<int32_t, int64_t>& heartbeat_timestamp_map_;
};

}  // namespace profiler

#endif  // PERFD_PROFILER_SERVICE_H_
