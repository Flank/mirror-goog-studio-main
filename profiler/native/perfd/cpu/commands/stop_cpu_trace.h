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
#ifndef PERFD_COMMANDS_STOP_CPU_TRACE_H
#define PERFD_COMMANDS_STOP_CPU_TRACE_H

#include "daemon/daemon.h"
#include "perfd/cpu/trace_manager.h"
#include "proto/commands.pb.h"

namespace profiler {

class StopCpuTrace : public CommandT<StopCpuTrace> {
 public:
  StopCpuTrace(const proto::Command& command, TraceManager* trace_manager)
      : CommandT(command), trace_manager_(trace_manager) {}

  static Command* Create(const proto::Command& command,
                         TraceManager* trace_manager) {
    return new StopCpuTrace(command, trace_manager);
  }

  virtual grpc::Status ExecuteOn(Daemon* daemon) override;

 private:
  TraceManager* trace_manager_;
};

}  // namespace profiler

#endif  // PERFD_COMMANDS_STOP_CPU_TRACE_H
