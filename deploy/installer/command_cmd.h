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

#ifndef COMMANDCMD_H
#define COMMANDCMD_H

#include "shell_command.h"

#include "apk_retriever.h"

namespace deployer {

// Wrapper around Android executable "service client".
class CmdCommand : public ShellCommandRunner {
 public:
  CmdCommand();
  bool GetAppApks(const std::string& package_name, Apks* apks,
                  std::string* error_string) const noexcept;

  bool AttachAgent(int pid, const std::string& agent, const std::string& args,
                   std::string* error_string) const noexcept;
};

}  // namespace deployer
#endif  // COMMANDCMD_H