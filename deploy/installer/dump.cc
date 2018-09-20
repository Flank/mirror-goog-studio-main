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
#include "tools/base/deploy/proto/deploy.pb.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

DumpCommand::DumpCommand() {}

void DumpCommand::ParseParameters(int argc, char** argv) {
  if (argc < 1) {
    return;
  }

  packageName_ = argv[0];
  ready_to_run_ = true;
}

void DumpCommand::Run(Workspace& workspace) {
  Trace traceDump("dump");

  proto::DumpResponse* response = new proto::DumpResponse();
  LogEvent(response->add_events(), "Starting dumping");
  workspace.GetResponse().set_allocated_dump_response(response);

  // Clean dump files from previous runs.
  std::string dumpBase_ = workspace.GetDumpsFolder() + packageName_ + "/";
  mkdir(dumpBase_.c_str(), S_IRWXG | S_IRWXU | S_IRWXO);
  workspace.ClearDirectory(dumpBase_.c_str());

  // Retrieve apks for this package.
  ApkRetriever apkRetriever(packageName_);
  bool success = true;
  auto apks_path = apkRetriever.get();
  if (apks_path.size() == 0) {
    response->set_status(proto::DumpResponse::ERROR);
    ErrEvent(response->add_events(), "ApkRetriever did not return apks");
    return;
  }

  // Extract all apks.
  for (std::string& apkPath : apks_path) {
    ApkArchive archive(apkPath);
    success &= archive.ExtractMetadata(packageName_, dumpBase_);
  }
  LogEvent(response->add_events(), "Done dumping");
  response->set_status(proto::DumpResponse::OK);
}

}  // namespace deploy
