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

// TODO: Use conditional project include when Bazel supports it.
#if !defined(__ANDROID__)

#include <dirent.h>

namespace deployer {

namespace {
const std::string kFakeAppsBase = "/tmp/.ir2/fakeapps/";
}  // namespace

void ApkRetriever::retrieve() noexcept {
  std::string folderToScan = kFakeAppsBase + packageName_;
  DIR* dir;
  struct dirent* ep;

  dir = opendir(folderToScan.c_str());
  if (dir != nullptr) {
    ep = readdir(dir);
    while (ep) {
      if (ep->d_name[0] != '.') {
        apks_.push_back(folderToScan + "/" + ep->d_name);
      }
      ep = readdir(dir);
    }
    closedir(dir);
  }
}

}  // namespace deployer

#endif  // !ANDROID
