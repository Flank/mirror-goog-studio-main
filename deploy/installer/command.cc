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

#include "command.h"

#include <functional>
#include <unordered_map>

#include "dump.h"

namespace deployer {



// Search dispatch table for a Command object matching the command name.
std::unique_ptr<Command> GetCommand(const char *command_name) {

  // Dispatch table mapping a command string to a Command object.
  static std::unordered_map<std::string, std::function<Command*(void)>>
          commandsRegister = {
          {"dump", [](){ return new DumpCommand();}}
          // Add here more commands (e.g: version, install, patch, agent, ...)
  };

  if (commandsRegister.find(command_name) == commandsRegister.end()) {
    return nullptr;
  }
  auto command_instantiator = commandsRegister[command_name];
  std::unique_ptr<Command> ptr;
  ptr.reset(command_instantiator());
  return ptr;
}

}  // namespace deployer
