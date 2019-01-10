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

#ifndef INSTALLER_APK_TOOLKIT_H_
#define INSTALLER_APK_TOOLKIT_H_

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/workspace.h"

#include <string>

namespace deploy {

class DumpCommand : public Command {
 public:
  DumpCommand(Workspace& workspace) : Command(workspace) {}
  virtual ~DumpCommand() {}
  virtual void ParseParameters(int argc, char** argv);
  virtual void Run();

 private:
  std::vector<std::string> package_names_;

  std::vector<std::string> RetrieveApks(const std::string& package_name);

  struct ProcStats {
    char name[16];
    int pid;
    int ppid;
    int uid;

    ProcStats() : name(), pid(0), ppid(0), uid(0) {}
  };

  bool GetApks(const std::string& package_name,
               proto::PackageDump* package_dump);
  bool GetProcessIds(const std::string& package_name,
                     proto::PackageDump* package_dump);
  bool ParseProc(dirent* entry, ProcStats* stats);
};

}  // namespace deploy

#endif  // INSTALLER_APK_TOOLKIT_H_
