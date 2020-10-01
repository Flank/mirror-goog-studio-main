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

#include <string>
#include <vector>

#include "tools/base/deploy/installer/workspace.h"

namespace deploy {

// Information on an ART process.
struct ProcessRecord {
  std::string process_name;
  bool crashing;
  bool not_responding;
};

// Wrapper around Android executable "service client".
class CmdCommand {
 public:
  explicit CmdCommand(const Workspace& workspace, Executor& executor)
      : executor_(executor),
        pm_exec_(workspace.GetPmPath()),
        cmd_exec_(workspace.GetCmdPath()) {}

  explicit CmdCommand(const Workspace& workspace)
      : CmdCommand(workspace, Executor::Get()) {}

  bool GetApks(const std::string& package_name, std::vector<std::string>* apks,
               std::string* error_string) const noexcept;

  bool AttachAgent(int pid, const std::string& agent, const std::string& args,
                   std::string* error_string) const noexcept;

  bool UpdateAppInfo(const std::string& user_id,
                     const std::string& package_name,
                     std::string* error_string) const noexcept;

  bool CreateInstallSession(std::string* session,
                            const std::vector<std::string> options) const
      noexcept;

  bool CommitInstall(const std::string& session, std::string* output) const
      noexcept;

  bool AbortInstall(const std::string& session, std::string* output) const
      noexcept;

  bool GetProcessInfo(const std::string& package_name,
                      std::vector<ProcessRecord>* records) const noexcept;

 private:
  Executor& executor_;

  // Path to the Android package manager executable, or a test mock.
  const std::string& pm_exec_;

  // Path to the Android cmd executable, or a test mock.
  const std::string& cmd_exec_;

  bool GetApksFromPath(const std::string& exec_path,
                       const std::string& package_name,
                       std::vector<std::string>* apks,
                       std::string* error_string) const noexcept;

  bool GetApksFromDump(const std::string& package_name,
                       std::vector<std::string>* apks,
                       std::string* error_string) const noexcept;
};

}  // namespace deploy
#endif  // COMMANDCMD_H