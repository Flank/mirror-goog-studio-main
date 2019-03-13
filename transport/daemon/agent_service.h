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
#ifndef DAEMON_AGENT_SERVICE_H_
#define DAEMON_AGENT_SERVICE_H_

#include <grpc++/grpc++.h>
#include <condition_variable>
#include <mutex>

#include "proto/agent_service.grpc.pb.h"
#include "utils/clock.h"

#include <unordered_map>

namespace profiler {

class Daemon;

class AgentServiceImpl : public proto::AgentService::Service {
 public:
  explicit AgentServiceImpl(Daemon* daemon) : daemon_(daemon) {}

  grpc::Status HeartBeat(grpc::ServerContext* context,
                         const proto::HeartBeatRequest* request,
                         proto::HeartBeatResponse* response) override;

  grpc::Status SendEvent(grpc::ServerContext* context,
                         const proto::SendEventRequest* request,
                         proto::EmptyResponse* response) override;

  grpc::Status SendBytes(grpc::ServerContext* context,
                         const proto::SendBytesRequest* request,
                         proto::EmptyResponse* response) override;

  grpc::Status RegisterAgent(
      grpc::ServerContext* context, const proto::RegisterAgentRequest* request,
      grpc::ServerWriter<proto::Command>* writer) override;

  // Sends a command to the agent. Returns true if the signal is sent, false
  // otherwise (if the agent is not alive).
  bool SendCommandToAgent(const proto::Command& command);

 private:
  Daemon* daemon_;

  std::mutex status_mutex_;
  std::mutex command_mutex_;
  std::condition_variable command_cv_;

  // Per-app flag which indicates whether a daemon->agent grpc streaming call
  // (RegisterAgent) has been established. Value is true if a stream is alive,
  // false otherwise.
  std::map<int32_t, bool> app_command_stream_statuses_;
  // A pid-to-Command mapping for pending commands.
  std::map<int32_t, proto::Command> pending_commands_;
};

}  // namespace profiler

#endif  // DAEMON_AGENT_SERVICE_H_
