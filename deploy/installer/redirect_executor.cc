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

#include "tools/base/deploy/installer/redirect_executor.h"

namespace deploy {

bool RedirectExecutor::RunWithInput(const std::string& executable_path,
                                    const std::vector<std::string>& parameters,
                                    std::string* output, std::string* error,
                                    const std::string& input_file) const {
  std::vector<std::string> args;
  args.insert(args.end(), args_.begin(), args_.end());
  args.push_back(executable_path);
  args.insert(args.end(), parameters.begin(), parameters.end());
  return executor_.RunWithInput(executable_, args, output, error, input_file);
}

bool RedirectExecutor::Run(const std::string& executable_path,
                           const std::vector<std::string>& parameters,
                           std::string* output, std::string* error) const {
  std::vector<std::string> args;
  args.insert(args.end(), args_.begin(), args_.end());
  args.push_back(executable_path);
  args.insert(args.end(), parameters.begin(), parameters.end());
  return executor_.Run(executable_, args, output, error);
}

bool RedirectExecutor::ForkAndExec(const std::string& executable_path,
                                   const std::vector<std::string>& parameters,
                                   int* child_stdin_fd, int* child_stdout_fd,
                                   int* child_stderr_fd, int* fork_pid) const {
  std::vector<std::string> args;
  args.insert(args.end(), args_.begin(), args_.end());
  args.push_back(executable_path);
  args.insert(args.end(), parameters.begin(), parameters.end());
  return executor_.ForkAndExec(executable_, args, child_stdin_fd,
                               child_stdout_fd, child_stderr_fd, fork_pid);
}
}  // namespace deploy