/*
 * Copyright (C) 2018 The Android Open Source Project
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
#ifndef ECHO_DAEMON_COMMAND_H_
#define ECHO_DAEMON_COMMAND_H_

#include "daemon/daemon.h"
#include "proto/echo.grpc.pb.h"
#include "proto/profiler.grpc.pb.h"

namespace demo {
/**
 * This class is a simple example of how to implement a command that is handled
 * by the daemon. This command simply takes the incomming data prepends "<from
 * Daemon> " and returns the message as an event.
 */
class EchoDaemonCommand : public profiler::CommandT<EchoDaemonCommand> {
 public:
  EchoDaemonCommand(const profiler::proto::Command& command,
                    const echo::EchoData& data)
      : CommandT(command), data_(data) {}

  static profiler::Command* Create(const profiler::proto::Command& command) {
    return new EchoDaemonCommand(command, command.echo_data());
  }

  virtual grpc::Status ExecuteOn(profiler::Daemon* daemon) override;

 private:
  echo::EchoData data_;
};

}  // namespace demo

#endif  // ECHO_DAEMON_COMMAND_H_
