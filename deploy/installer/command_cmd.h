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

// Wrapper around Android executable "service client".
class CmdCommand {
 public:
  CmdCommand(Workspace& workspace) : workspace_(workspace) {}

  bool GetAppApks(const std::string& package_name,
                  std::vector<std::string>* apks,
                  std::string* error_string) const noexcept;

  bool DumpApks(const std::string& package_name, std::vector<std::string>* apks,
                std::string* error_string) const noexcept;

  bool AttachAgent(int pid, const std::string& agent, const std::string& args,
                   std::string* error_string) const noexcept;

  bool UpdateAppInfo(const std::string& user_id,
                     const std::string& package_name,
                     std::string* error_string) const noexcept;

  bool CreateInstallSession(std::string* session,
                            const std::vector<std::string> options) const
      noexcept;

  // Prepares an installation and returns an id that can be used
  // to finish the installation by calling |CommitInstall| or it
  // can be aborted by calling |AbortInstall|
  int PreInstall(const std::vector<std::string>& apks,
                 std::string* output) const noexcept;

  bool CommitInstall(const std::string& session, std::string* output) const
      noexcept;

  bool AbortInstall(const std::string& session, std::string* output) const
      noexcept;

  static void SetPath(const char* path);

 private:
  Workspace& workspace_;
};

}  // namespace deploy
#endif  // COMMANDCMD_H