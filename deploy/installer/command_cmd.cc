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

#include "command_cmd.h"
#include <cstring>
#include <iostream>
#include <sstream>
#include "trace.h"

namespace deploy {

namespace {
const char* CMD_EXEC = "/system/bin/cmd";
}  // namespace

CmdCommand::CmdCommand() : ShellCommandRunner(CMD_EXEC) {}

bool CmdCommand::GetAppApks(const std::string& package_name, Apks* apks,
                            std::string* error_string) const noexcept {
  Trace trace("CmdCommand::GetAppApks");
  std::string parameters;
  parameters.append("package ");
  parameters.append("path ");
  parameters.append(package_name);
  std::string output;
  bool success = Run(parameters, &output);
  if (!success) {
    *error_string = output;
    std::cerr << output;
    return false;
  }

  // Parse output
  std::stringstream ss(output);
  std::string line;

  // Return path prefixed with "package:"
  while (std::getline(ss, line, '\n')) {
    if (!strncmp(line.c_str(), "package:", 8)) {
      apks->push_back(std::string(line.c_str() + 8));
    }
  }

  return true;
}

bool CmdCommand::AttachAgent(int pid, const std::string& agent,
                             const std::string& args,
                             std::string* error_string) const noexcept {
  Trace trace("CmdCommand::AttachAgent");
  std::stringstream parameters;
  parameters << "activity ";
  parameters << "attach-agent ";
  parameters << pid << " ";
  parameters << agent << "=" << args;

  return Run(parameters.str(), error_string);
}

bool CmdCommand::UpdateAppInfo(const std::string& user_id,
                               const std::string& package_name,
                               std::string* error_string) const noexcept {
  Trace trace("CmdCommand::UpdateAppInfo");
  std::stringstream parameters;
  parameters << "activity ";
  parameters << "update-appinfo ";
  parameters << user_id << " ";
  parameters << package_name << " ";

  return Run(parameters.str(), error_string);
}

void CmdCommand::SetPath(const char* path) { CMD_EXEC = path; }

}  // namespace deploy