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

#ifndef BASH_COMMAND_RUNNER_H
#define BASH_COMMAND_RUNNER_H

#include <string>

namespace deployer {

// Run bash commands.
class ShellCommandRunner {
 public:
  // Expected executable_path can be either absolute, relative or even
  // executable name only path.
  explicit ShellCommandRunner(const std::string &executable_path);
  virtual ~ShellCommandRunner() = default;
  // If |output| is not null, it is populated with stdin and stderr from
  // running command.
  virtual bool Run(const std::string &parameters, std::string *output) const;
  bool RunAs(const std::string &parameters, const std::string &username,
             std::string *output) const;

 private:
  const std::string executable_path_;
  virtual bool RunAndReadOutput(const std::string &cmd,
                                std::string *output) const;
};

}  // namespace deployer
#endif  // BASH_COMMAND_RUNNER_H