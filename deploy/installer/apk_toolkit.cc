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

#include "apk_toolkit.h"

#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <iostream>

#include "apk_archive.h"
#include "apk_retriever.h"

namespace deployer {

namespace {
const char* kBasename = ".ir2";
uint32_t kPathMax = 1024;
}  // namespace

ApkToolkit::ApkToolkit(const char* packageName) : packageName_(packageName) {
  base_ = getBase();
  constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
  std::string dumpFolder = base_ + "/dumps/";
  mkdir(dumpFolder.c_str(), kDirectoryMode);

  dumpBase_ = dumpFolder + packageName_ + "/";
  mkdir(dumpBase_.c_str(), kDirectoryMode);
}

// Retrieves the base folder which is expected to be ".ir2" somewhere in the
// path.e.g: /data/local/tmp/.ir2/bin base is /data/local/tmp/.ir2 .
std::string ApkToolkit::getBase() {
  char cwdbuffer[kPathMax];
  getcwd(cwdbuffer, kPathMax);
  char* directoryCursor = cwdbuffer;

  // Search for ".ir2" folder.
  while (directoryCursor[0] != '/' || directoryCursor[1] != 0) {
    directoryCursor = dirname(directoryCursor);
    if (!strcmp(kBasename, basename(directoryCursor))) {
      return directoryCursor;
    }
  }
  std::cerr << "Unable to find '" << kBasename << "' base folder in '"
            << cwdbuffer << "'" << std::endl;
  return "";
}

bool ApkToolkit::extractCDsandSignatures() const noexcept {
  std::cout << "Using base   : '" << base_ << "'" << std::endl;
  std::cout << "Package name : '" << packageName_ << "'" << std::endl;

  ApkRetriever apkRetriever(packageName_);
  for (std::string& apkPath : apkRetriever.get()) {
    std::cout << "Processing apk: '" << apkPath << "'" << std::endl;
    ApkArchive archive(apkPath);
    archive.ExtractMetadata(packageName_, dumpBase_);
  }
  return true;
}

}  // namespace deployer
