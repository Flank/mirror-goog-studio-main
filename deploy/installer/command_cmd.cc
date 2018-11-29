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

#include "tools/base/deploy/installer/command_cmd.h"

#include <iostream>
#include <sstream>

#include <sys/stat.h>
#include <cstdlib>
#include <cstring>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor.h"

namespace deploy {

namespace {
const char* CMD_EXEC = "/system/bin/cmd";
}  // namespace

CmdCommand::CmdCommand() {}

bool CmdCommand::GetAppApks(const std::string& package_name,
                            std::vector<std::string>* apks,
                            std::string* error_string) const noexcept {
  Trace trace("CmdCommand::GetAppApks");
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("path");
  parameters.emplace_back(package_name);
  std::string out;
  std::string err;
  bool success = Executor::Run(CMD_EXEC, parameters, &out, &err);
  if (!success) {
    *error_string = err;
    return false;
  }

  // Parse output
  std::stringstream ss(out);
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
  std::vector<std::string> parameters;
  parameters.emplace_back("activity");
  parameters.emplace_back("attach-agent");
  parameters.emplace_back(to_string(pid));
  parameters.emplace_back(agent + "=" + args);

  std::string out;
  return Executor::Run(CMD_EXEC, parameters, &out, error_string);
}

bool CmdCommand::UpdateAppInfo(const std::string& user_id,
                               const std::string& package_name,
                               std::string* error_string) const noexcept {
  Trace trace("CmdCommand::UpdateAppInfo");
  std::vector<std::string> parameters;
  parameters.emplace_back("activity");
  parameters.emplace_back("update-appinfo");
  parameters.emplace_back(user_id);
  parameters.emplace_back(package_name);

  std::string out;
  return Executor::Run(CMD_EXEC, parameters, &out, error_string);
}

int get_file_size(std::string path) {
  struct stat statbuf;
  stat(path.c_str(), &statbuf);
  return statbuf.st_size;
}

bool CmdCommand::CreateInstallSession(std::string* output) const noexcept {
  Phase p("Create Install Session");
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-create");
  parameters.emplace_back("-t");
  parameters.emplace_back("-r");
  parameters.emplace_back("--dont-kill");
  std::string err;
  Executor::Run(CMD_EXEC, parameters, output, &err);
  std::string match = "Success: created install session [";
  if (output->find(match, 0) != 0) {
    return false;
  }
  *output = output->substr(match.size(), output->size() - match.size() - 2);
  return true;
}

int CmdCommand::PreInstall(const std::vector<std::string>& apks,
                           std::string* output) const noexcept {
  Phase p("Preinstall");
  output->clear();

  std::string session;
  if (!CreateInstallSession(output)) {
    return -1;
  } else {
    session = *output;
  }

  output->clear();
  for (auto& apk : apks) {
    std::string output;
    std::string error;
    std::vector<std::string> parameters;
    parameters.push_back("package");
    parameters.push_back("install-write");
    std::stringstream size;
    size << "-S" << get_file_size(apk);
    parameters.push_back(size.str());
    parameters.push_back(session);
    parameters.push_back(apk.substr(apk.rfind("/") + 1));
    Executor::RunWithInput(CMD_EXEC, parameters, &output, &error, apk);
  }
  return atoi(session.c_str());
}  // namespace deploy

bool CmdCommand::CommitInstall(const std::string& session,
                               std::string* output) const noexcept {
  Phase p("Commit Install");
  output->clear();
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-commit");
  parameters.emplace_back(session);
  std::string err;
  return Executor::Run(CMD_EXEC, parameters, output, &err);
}

bool CmdCommand::AbortInstall(const std::string& session,
                              std::string* output) const noexcept {
  std::vector<std::string> parameters;
  parameters.emplace_back("package");
  parameters.emplace_back("install-abandon");
  parameters.emplace_back(session);
  output->clear();
  std::string err;
  return Executor::Run(CMD_EXEC, parameters, output, &err);
}

void CmdCommand::SetPath(const char* path) { CMD_EXEC = path; }

}  // namespace deploy