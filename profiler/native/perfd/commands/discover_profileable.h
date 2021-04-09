/*
 * Copyright (C) 2021 The Android Open Source Project
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
#ifndef PERFD_COMMANDS_DISCOVER_PROFILEABLE_H_
#define PERFD_COMMANDS_DISCOVER_PROFILEABLE_H_

#include "daemon/daemon.h"
#include "proto/transport.grpc.pb.h"

namespace profiler {

class DiscoverProfileable : public CommandT<DiscoverProfileable> {
 public:
  DiscoverProfileable(const proto::Command& command) : CommandT(command) {}

  static Command* Create(const proto::Command& command) {
    return new DiscoverProfileable(command);
  }

  virtual grpc::Status ExecuteOn(Daemon* daemon) override;
};

}  // namespace profiler

#endif  // PERFD_COMMANDS_DISCOVER_PROFILEABLE_H_
