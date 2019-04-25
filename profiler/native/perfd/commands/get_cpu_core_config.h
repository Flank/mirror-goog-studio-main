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
#ifndef PERFD_COMMANDS_GET_CPU_CORE_CONFIG_H_
#define PERFD_COMMANDS_GET_CPU_CORE_CONFIG_H_

#include "daemon/daemon.h"
#include "proto/commands.pb.h"

namespace profiler {

class GetCpuCoreConfig : public CommandT<GetCpuCoreConfig> {
 public:
  GetCpuCoreConfig(const proto::Command& command,
                   const proto::GetCpuCoreConfig& data)
      : CommandT(command), data_(data) {}

  static Command* Create(const proto::Command& command) {
    return new GetCpuCoreConfig(command, command.get_cpu_core_config());
  }

  virtual grpc::Status ExecuteOn(Daemon* daemon) override;

 private:
  proto::GetCpuCoreConfig data_;
};

}  // namespace profiler

#endif  // PERFD_COMMANDS_GET_CPU_CORE_CONFIG_H_
