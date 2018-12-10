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

#ifndef EXECUTOR_IMPL_H
#define EXECUTOR_IMPL_H

#include <string>
#include <vector>

#include "tools/base/deploy/installer/executor.h"

namespace deploy {

class ExecutorImpl : public Executor {
 public:
  ExecutorImpl(const std::string& run_as_exec) : run_as_exec_(run_as_exec) {}

  bool Run(const std::string& executable_path,
           const std::vector<std::string>& args, std::string* output,
           std::string* error) const;

  bool RunAs(const std::string& executable_path,
             const std::string& package_name,
             const std::vector<std::string>& args, std::string* output,
             std::string* error) const;

  bool RunWithInput(const std::string& executable_path,
                    const std::vector<std::string>& args, std::string* output,
                    std::string* error, const std::string& input_file) const;

  bool ForkAndExec(const std::string& executable_path,
                   const std::vector<std::string>& parameters,
                   int* child_stdin_fd, int* child_stdout_fd,
                   int* child_stderr_fd, int* fork_pid) const;

  bool ForkAndExecAs(const std::string& executable_path,
                     const std::string& package_name,
                     const std::vector<std::string>& parameters,
                     int* child_stdin_fd, int* child_stdout_fd,
                     int* child_stderr_fd, int* fork_pid) const;

 private:
  std::string run_as_exec_;

  void Pump(int stdin_source, int child_stdin, int child_stdout,
            std::string* output, int child_stderr, std::string* error) const;

  bool PrivateRun(const std::string& executable_path,
                  const std::vector<std::string>& args, std::string* output,
                  std::string* error, int input_file_fd) const;
};

}  // namespace deploy

#endif  // EXECUTOR_IMPL_H