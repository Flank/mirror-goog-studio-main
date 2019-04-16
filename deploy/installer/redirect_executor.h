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

#ifndef REDIRECT_EXECUTOR_H
#define REDIRECT_EXECUTOR_H

#include <string>
#include <vector>

#include "tools/base/deploy/installer/executor.h"

namespace deploy {

class RedirectExecutor : public Executor {
 public:
  RedirectExecutor(const std::string& executable,
                   const std::vector<std::string>& args, Executor& executor)
      : executor_(executor), executable_(executable), args_(args) {}

  RedirectExecutor(const std::string& executable, const std::string& arg,
                   Executor& executor)
      : executor_(executor), executable_(executable) {
    args_.push_back(arg);
  }

  bool Run(const std::string& executable_path,
           const std::vector<std::string>& args, std::string* output,
           std::string* error) const;

  bool RunWithInput(const std::string& executable_path,
                    const std::vector<std::string>& args, std::string* output,
                    std::string* error, const std::string& input_file) const;

  bool ForkAndExec(const std::string& executable_path,
                   const std::vector<std::string>& parameters,
                   int* child_stdin_fd, int* child_stdout_fd,
                   int* child_stderr_fd, int* fork_pid) const;

 private:
  Executor& executor_;
  std::string executable_;
  std::vector<std::string> args_;
};

}  // namespace deploy

#endif  // REDIRECT_EXECUTOR_H