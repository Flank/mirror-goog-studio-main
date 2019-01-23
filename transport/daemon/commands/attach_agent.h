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
#ifndef DAEMON_COMMANDS_ATTACH_AGENT_H_
#define DAEMON_COMMANDS_ATTACH_AGENT_H_

#include "daemon/daemon.h"
#include "proto/profiler.grpc.pb.h"

namespace profiler {

class AttachAgent : public CommandT<AttachAgent> {
 public:
  AttachAgent(const proto::Command &command, const proto::AttachAgent &data)
      : CommandT(command), data_(data) {}

  static Command *Create(const proto::Command &command) {
    return new AttachAgent(command, command.attach_agent());
  }

  virtual grpc::Status ExecuteOn(Daemon *daemon) override;

 private:
  proto::AttachAgent data_;
};

}  // namespace profiler

#endif  // DAEMON_COMMANDS_ATTACH_AGENT_H_
