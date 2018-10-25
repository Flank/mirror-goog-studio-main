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

#ifndef COMMAND_H
#define COMMAND_H

#include <memory>

#include "tools/base/deploy/installer/workspace.h"

namespace deploy {

// Base class which all Command object (dump, and future patch, version, agent)
// should extend.
class Command {
 public:
  virtual ~Command() = default;
  // Parse parameters and set readyToRun to true if no error was encountered.
  virtual void ParseParameters(int argc, char** argv) = 0;

  // Execute command.
  virtual void Run(Workspace& workspace) = 0;

  bool ReadyToRun() { return ready_to_run_; }

 protected:
  bool ready_to_run_ = false;
};

// Search dispatch table for a Command object matching the command name.
std::unique_ptr<Command> GetCommand(const char* command_name);

}  // namespace deploy

#endif
