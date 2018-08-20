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
#ifndef PERFD_COMMANDS_COMMAND_H_
#define PERFD_COMMANDS_COMMAND_H_

#include <grpc++/grpc++.h>
#include "perfd/daemon.h"
#include "proto/profiler.grpc.pb.h"

namespace profiler {

class Daemon;

class Command {
 public:
  Command(const proto::Command& command) : command_(command) {}
  virtual ~Command() {}

  const proto::Command& command() { return command_; }

  // Executes the command on the given daemon. This is guaranteed
  // to run holding the daemon's lock.
  virtual grpc::Status ExecuteOn(Daemon* daemon) = 0;

 protected:
  proto::Command command_;
};

template <class T>
class CommandT : public Command {
 public:
  CommandT(const proto::Command& command) : Command(command) {}
};
}  // namespace profiler

#endif  // PERFD_COMMANDS_COMMAND_H_
