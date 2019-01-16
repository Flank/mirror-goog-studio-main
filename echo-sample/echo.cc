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

#include "echo.h"
#include <cstring>
#include "commands/echo_daemon_command.h"

namespace demo {

void Echo::Initialize(profiler::Daemon* daemon) {
  // Registers echo command handler for daemon commands.
  daemon->RegisterCommandHandler(profiler::proto::Command::ECHO,
                                 &EchoDaemonCommand::Create);
}

}  // namespace demo