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

#ifndef DEPLOYER_DELTA_INSTALL_H
#define DEPLOYER_DELTA_INSTALL_H

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class DeltaInstallCommand : public Command {
 public:
  explicit DeltaInstallCommand(Workspace& workspace);
  virtual ~DeltaInstallCommand() = default;
  virtual void ParseParameters(int argc, char** argv);
  virtual void Run();

 private:
  bool SendApkToPackageManager(const proto::PatchInstruction& patch,
                               const std::string& session_id);
  // Install using pm install interface which requires temporary hard storage.
  void Install();
  // Install using pm install-create, install-write, install-commit streaming
  // API where data is streamed directely to the Package Manager.
  void StreamInstall();
  proto::DeltaInstallRequest request_;
};

}  // namespace deploy

#endif  // DEPLOYER_DELTA_INSTALL_H
