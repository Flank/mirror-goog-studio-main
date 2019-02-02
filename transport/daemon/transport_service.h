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
#ifndef DAEMON_TRANSPORT_SERVICE_H_
#define DAEMON_TRANSPORT_SERVICE_H_

#include <grpc++/grpc++.h>

#include "daemon/commands/command.h"
#include "proto/common.grpc.pb.h"
#include "proto/transport.grpc.pb.h"

namespace profiler {

class Daemon;

class TransportServiceImpl final
    : public profiler::proto::TransportService::Service {
 public:
  explicit TransportServiceImpl(Daemon* daemon) : daemon_(daemon) {}

  grpc::Status GetCurrentTime(grpc::ServerContext* context,
                              const proto::TimeRequest* request,
                              proto::TimeResponse* response) override;

  grpc::Status GetVersion(grpc::ServerContext* context,
                          const proto::VersionRequest* request,
                          proto::VersionResponse* response) override;

  grpc::Status GetBytes(grpc::ServerContext* context,
                        const proto::BytesRequest* request,
                        proto::BytesResponse* response) override;

  grpc::Status GetAgentStatus(grpc::ServerContext* context,
                              const proto::AgentStatusRequest* request,
                              proto::AgentData* response) override;

  grpc::Status ConfigureStartupAgent(
      grpc::ServerContext* context,
      const proto::ConfigureStartupAgentRequest* request,
      proto::ConfigureStartupAgentResponse* response) override;

  grpc::Status Execute(grpc::ServerContext* context,
                       const proto::ExecuteRequest* request,
                       proto::ExecuteResponse* response) override;

  grpc::Status GetEvents(grpc::ServerContext* context,
                         const proto::GetEventsRequest* request,
                         grpc::ServerWriter<proto::Event>* response) override;

  grpc::Status GetEventGroups(grpc::ServerContext* context,
                              const proto::GetEventGroupsRequest* request,
                              proto::GetEventGroupsResponse* response) override;

 private:
  // The daemon this service talks to.
  Daemon* daemon_;
};

}  // namespace profiler

#endif  // DAEMON_TRANSPORT_SERVICE_H_
