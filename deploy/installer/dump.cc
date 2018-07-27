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

#include "dump.h"

#include <dirent.h>
#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <iostream>

#include "apk_archive.h"
#include "apk_retriever.h"
#include "trace.h"

namespace deployer {

namespace {
const char* kBasename = ".ir2";
uint32_t kPathMax = 1024;
}  // namespace

DumpCommand::DumpCommand() {}

// Retrieves the base folder which is expected to be ".ir2" somewhere in the
// path.e.g: /data/local/tmp/.ir2/bin base is /data/local/tmp/.ir2 .
// TODO: Create an object "Workspace" to handle all filesystem work.
std::string DumpCommand::GetBase() {
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

void DumpCommand::ParseParameters(int argc, char** argv) {
  if (argc < 1) {
    return;
  }

  packageName_ = argv[0];
  ready_to_run_ = true;
}

bool DumpCommand::Run() {
  std::string base_ = GetBase();
  constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
  std::string dumpFolder = base_ + "/dumps/";
  mkdir(dumpFolder.c_str(), kDirectoryMode);

  std::string dumpBase_ = dumpFolder + packageName_ + "/";
  mkdir(dumpBase_.c_str(), kDirectoryMode);

  // Unlink all files which could have been generated from a previous run.
  ClearDirectory(dumpBase_.c_str());

  ApkRetriever apkRetriever(packageName_);
  for (std::string& apkPath : apkRetriever.get()) {
    ApkArchive archive(apkPath);
    archive.ExtractMetadata(packageName_, dumpBase_);
  }
  return true;
}
void DumpCommand::ClearDirectory(const char* dirname) const noexcept {
  DIR* dir;
  struct dirent* entry;
  char path[PATH_MAX];
  dir = opendir(dirname);
  while ((entry = readdir(dir)) != NULL) {
    if (strcmp(entry->d_name, ".") && strcmp(entry->d_name, "..")) {
      snprintf(path, (size_t)PATH_MAX, "%s/%s", dirname, entry->d_name);
      if (entry->d_type == DT_DIR) {
        ClearDirectory(path);
      }
      unlink(path);
    }
  }
  closedir(dir);
}

}  // namespace deployer
