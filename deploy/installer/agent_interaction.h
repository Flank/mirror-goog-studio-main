/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef AGENT_INTERACTION_H
#define AGENT_INTERACTION_H

#include <google/protobuf/repeated_field.h>

#include <string>
#include <vector>

#include "tools/base/deploy/installer/command.h"

namespace deploy {

class AgentInteractionCommand : public Command {
 public:
  AgentInteractionCommand(Workspace& workspace) : Command(workspace) {}
  ~AgentInteractionCommand() = default;

 protected:
  // Tries to attach an agent to each process in the request; if any agent fails
  // to attach, returns false.
  bool Attach(const std::vector<int>& pids, const std::string& agent_path);
  bool Attach(const google::protobuf::RepeatedField<int>& pids,
              const std::string& agent_path);

  std::string GetSocketName();

 private:
  std::string socket_name_;
};
}  // namespace deploy

#endif
