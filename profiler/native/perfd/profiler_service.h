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

#include "perfd/commands/command.h"
#include "perfd/daemon.h"
#include "proto/profiler.grpc.pb.h"

namespace profiler {

class ProfilerServiceImpl final
    : public profiler::proto::ProfilerService::Service {
 public:
  explicit ProfilerServiceImpl(Daemon* daemon) : daemon_(daemon) {}

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
      profiler::proto::AgentData* response) override;

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

  grpc::Status GetSessions(
      grpc::ServerContext* context,
      const profiler::proto::GetSessionsRequest* request,
      profiler::proto::GetSessionsResponse* response) override;

  grpc::Status Execute(grpc::ServerContext* context,
                       const profiler::proto::ExecuteRequest* request,
                       profiler::proto::ExecuteResponse* response) override;

  grpc::Status GetEvents(grpc::ServerContext* context,
                         const profiler::proto::GetEventsRequest* request,
                         grpc::ServerWriter<proto::Event>* response) override;

  grpc::Status GetEventGroups(
      grpc::ServerContext* context,
      const profiler::proto::GetEventGroupsRequest* request,
      profiler::proto::GetEventGroupsResponse* response) override;

 private:
  // The daemon this service talks to.
  Daemon* daemon_;
};

}  // namespace profiler

#endif  // PERFD_PROFILER_SERVICE_H_
