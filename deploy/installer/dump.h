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

#include "command.h"

#include <string>

namespace deployer {

class DumpCommand : public Command {
 public:
  DumpCommand();
  virtual ~DumpCommand() {}
  virtual void ParseParameters(int argc, char** argv);
  virtual bool Run();

 private:
  // TODO: Move all these to a Workspace object (and re-use "fs" from profiler
  // for it).
  std::string GetBase();
  std::string packageName_;
};

}  // namespace deployer

#endif  // INSTALLER_APK_TOOLKIT_H_
