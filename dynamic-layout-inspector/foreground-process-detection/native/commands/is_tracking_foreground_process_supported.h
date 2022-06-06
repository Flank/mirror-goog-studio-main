/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef DYNAMIC_LAYOUT_INSPECTOR_FOREGROUND_PROCESS_DETECTION_COMMANDS_IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED_
#define DYNAMIC_LAYOUT_INSPECTOR_FOREGROUND_PROCESS_DETECTION_COMMANDS_IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED_

#include "daemon/commands/command.h"

#include <grpc++/grpc++.h>

#include "daemon/daemon.h"

namespace layout_inspector {

// A command handler to be registered with the transport daemon.
class IsTrackingForegroundProcessSupported
    : public profiler::CommandT<IsTrackingForegroundProcessSupported> {
 public:
  IsTrackingForegroundProcessSupported(const profiler::proto::Command& command)
      : CommandT(command) {}

  static Command* Create(const profiler::proto::Command& command) {
    return new IsTrackingForegroundProcessSupported(command);
  }

  virtual grpc::Status ExecuteOn(profiler::Daemon* daemon);
};

}  // namespace layout_inspector

#endif  // DYNAMIC_LAYOUT_INSPECTOR_FOREGROUND_PROCESS_DETECTION_COMMANDS_IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED_