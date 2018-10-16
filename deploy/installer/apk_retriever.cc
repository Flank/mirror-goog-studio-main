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

#include "tools/base/deploy/installer/apk_retriever.h"

#include <iostream>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/package_manager.h"

namespace deploy {

std::vector<std::string> ApkRetriever::retrieve(
    const std::string& package_name) const noexcept {
  Phase p("retrieve_apk_path");
  std::vector<std::string> apks;
  // First try with cmd. It may fail since path capability was added to "cmd" in
  // Android P.
  CmdCommand cmd;
  std::string errorOutput;
  cmd.GetAppApks(package_name, &apks, &errorOutput);
  if (apks.size() == 0) {
    // "cmd" likely failed. Try with PackageManager (pm)
    PackageManager pm;
    pm.GetApks(package_name, &apks, &errorOutput);
  }
  return apks;
}
}  // namespace deploy
