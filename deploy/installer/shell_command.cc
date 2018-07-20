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

#include "shell_command.h"

#include <iostream>
#include "sys/wait.h"
#include "trace.h"

using std::string;
namespace deployer {

const string kRunAsExecutable = "/system/bin/run-as";

ShellCommandRunner::ShellCommandRunner(const string& executable_path)
    : executable_path_(executable_path) {}

bool ShellCommandRunner::Run(const string& parameters, string* output) const {
  string cmd;
  cmd.append(executable_path_);
  if (!parameters.empty()) {
    cmd.append(" ");
    cmd.append(parameters);
  }
  return RunAndReadOutput(cmd, output);
}

bool ShellCommandRunner::RunAs(const string& parameters,
                               const string& package_name,
                               string* output) const {
  // This assumes "run as" was installed correctly and to the specified
  // location. We should validate this.
  string cmd = kRunAsExecutable;
  cmd.append(" ");
  cmd.append(package_name);
  cmd.append(" ");
  cmd.append(executable_path_);
  cmd.append(" ");
  cmd.append(parameters);

  return RunAndReadOutput(cmd, output);
}

bool ShellCommandRunner::RunAndReadOutput(const string& cmd,
                                          string* output) const {
  Trace trace(executable_path_);
  char buffer[1024];

  // Without this line, stdout is picked up but not stderr.
  string redirected_cmd = cmd + " 2>&1";
  FILE* pipe = popen(redirected_cmd.c_str(), "r");
  if (pipe == nullptr) {
    return false;
  }
  while (!feof(pipe)) {
    if (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
      if (output != nullptr) {
        output->append(buffer);
      }
    }
  }
  int ret = pclose(pipe);
  return WEXITSTATUS(ret) == 0;
}

}  // namespace deployer