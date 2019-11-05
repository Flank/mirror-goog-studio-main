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

#ifndef PACKAGEMANAGER_H
#define PACKAGEMANAGER_H

#include <string>
#include <vector>

#include "tools/base/deploy/installer/workspace.h"

namespace deploy {

// Wrapper around Android executable "pm" (Android Package Manager).
class PackageManager {
 public:
  PackageManager(Workspace& workspace) : workspace_(workspace) {}
  bool GetApks(const std::string& package_name, std::vector<std::string>* apks,
               std::string* error_string) const;
  static void SetPath(const char* path);

 private:
  Workspace& workspace_;
};

}  // namespace deploy

#endif  // PACKAGEMANAGER_H