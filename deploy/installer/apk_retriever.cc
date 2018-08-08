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

#include "apk_retriever.h"

#include <iostream>

#include "command_cmd.h"
#include "package_manager.h"

namespace deployer {

ApkRetriever::ApkRetriever(const std::string& packageName)
    : packageName_(packageName) {
  retrieve();
}

void ApkRetriever::retrieve() noexcept {
  // First try with cmd. It may fail since path capability was added to "cmd" in
  // Android P.
  CmdCommand cmd;
  std::string errorOutput;
  cmd.GetAppApks(packageName_, &apks_, &errorOutput);
  if (apks_.size() == 0) {
    std::cerr << "Unable to retrieve apks with 'cmd'." << std::endl;
    std::cerr << errorOutput << std::endl;
    // "cmd" likely failed. Try with PackageManager
    PackageManager pm;
    pm.GetApks(packageName_, &apks_, &errorOutput);
    if (apks_.size() == 0) {
      std::cerr << "Unable to retrieve apks with 'pm'." << std::endl;
      std::cerr << errorOutput << std::endl;
    }
  }
}

Apks& ApkRetriever::get() { return apks_; }

}  // namespace deployer
