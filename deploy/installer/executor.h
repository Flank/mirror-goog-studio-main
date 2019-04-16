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

#ifndef EXECUTOR_H
#define EXECUTOR_H

#include <string>
#include <vector>

namespace deploy {

// Interface allowing execution behavior to be mocked, in order to make it
// possible to unit test complex installer commands.
class Executor {
 public:
  virtual bool Run(const std::string& executable_path,
                   const std::vector<std::string>& args, std::string* output,
                   std::string* error) const = 0;

  virtual bool RunWithInput(const std::string& executable_path,
                            const std::vector<std::string>& args,
                            std::string* output, std::string* error,
                            const std::string& input_file) const = 0;

  virtual bool ForkAndExec(const std::string& executable_path,
                           const std::vector<std::string>& parameters,
                           int* child_stdin_fd, int* child_stdout_fd,
                           int* child_stderr_fd, int* fork_pid) const = 0;
};

}  // namespace deploy
#endif  // EXECUTOR_H