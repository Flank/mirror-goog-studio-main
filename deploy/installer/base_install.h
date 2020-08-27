/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef DEPLOYER_BASE_INSTALL_H
#define DEPLOYER_BASE_INSTALL_H

#include "tools/base/deploy/installer/command.h"

#include <string>
#include <vector>

namespace deploy {

class BaseInstallCommand : public Command {
 public:
  BaseInstallCommand(Workspace& workspace);
  virtual ~BaseInstallCommand() = default;
  virtual void ParseParameters(const proto::InstallerRequest& request);

 protected:
  bool CreateInstallSession(std::string* output,
                            std::vector<std::string>* options);
  bool SendApksToPackageManager(const std::string& session_id);
  proto::InstallInfo install_info_;

 private:
  bool SendApkToPackageManager(const proto::PatchInstruction& patch,
                               const std::string& session_id);
};

}  // namespace deploy

#endif  // DEPLOYER_BASE_INSTALL