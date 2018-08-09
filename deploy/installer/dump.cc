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

DumpCommand::DumpCommand() {}

void DumpCommand::ParseParameters(int argc, char** argv) {
  if (argc < 1) {
    return;
  }

  packageName_ = argv[0];
  ready_to_run_ = true;
}

bool DumpCommand::Run(const Workspace& workspace) {
  Trace traceDump("dump");
  std::string base_ = workspace.GetBase();
  constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
  std::string dumpFolder = base_ + "/dumps/";
  mkdir(dumpFolder.c_str(), kDirectoryMode);

  std::string dumpBase_ = dumpFolder + packageName_ + "/";
  mkdir(dumpBase_.c_str(), kDirectoryMode);

  // Unlink all files which could have been generated from a previous run.
  workspace.ClearDirectory(dumpBase_.c_str());

  ApkRetriever apkRetriever(packageName_);
  for (std::string& apkPath : apkRetriever.get()) {
    ApkArchive archive(apkPath);
    archive.ExtractMetadata(packageName_, dumpBase_);
  }
  return true;
}

}  // namespace deployer
