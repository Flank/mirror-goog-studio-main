/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef DEPLOY_HIGHLANDER_H
#define DEPLOY_HIGHLANDER_H

#include <string>

#include "tools/base/deploy/installer/workspace.h"

namespace deploy {
class Highlander {
 public:
  explicit Highlander(const Workspace& workspace);
  ~Highlander();

 private:
  std::string pid_file_path_;
  static void TerminateOtherInstances(const Workspace& workspace);
  void WritePid(const Workspace& workspace);
};

}  // namespace deploy

#endif  // DEPLOY_HIGHLANDER_H
