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
#include <vector>

namespace deploy {

class Executor {
 public:
  static bool RunWithInput(const std::string& executable_path,
                           const std::vector<std::string>& args,
                           std::string* output, std::string* error,
                           const std::string& input_file) noexcept;

  static bool Run(const std::string& executable_path,
                  const std::vector<std::string>& args, std::string* output,
                  std::string* error) noexcept;

  static bool RunAs(const std::string& executable_path,
                    const std::string& package_name,
                    const std::vector<std::string>& parameters,
                    std::string* output, std::string* error) noexcept;

  // Run a fork() and exec(). Returns the stdin, stderr, and stdout fd
  // which the caller MUST close. It is also the caller's responsibility
  // to call wait in order to reclaim the resource of the zombi process
  // left after exec() terminates.
  static bool ForkAndExec(const std::string& executable_path,
                          const std::vector<std::string>& parameters,
                          int* child_stdin_fd, int* child_stdout_fd,
                          int* child_stderr_fd, int* fork_pid) noexcept;

  // Run a fork() and exec() using a package username. Returns the stdin,
  // stderr, and stdout fd which the caller MUST close. It is also the
  // caller's responsibility to call wait in order to reclaim the resource
  // of the zombi process left after exec() terminates.
  static bool ForkAndExecAs(const std::string& executable_path,
                            const std::string& package_name,
                            const std::vector<std::string>& parameters,
                            int* child_stdin_fd, int* child_stdout_fd,
                            int* child_stderr_fd, int* fork_pid) noexcept;
};

}  // namespace deploy
#endif  // BASH_COMMAND_RUNNER_H