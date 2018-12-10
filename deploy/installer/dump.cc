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

#include "tools/base/deploy/installer/dump.h"

#include <iostream>

#include <dirent.h>
#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/apk_archive.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/package_manager.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

void DumpCommand::ParseParameters(int argc, char** argv) {
  if (argc < 1) {
    return;
  }

  packageName_ = argv[0];
  ready_to_run_ = true;
}

void DumpCommand::Run() {
  Phase p("Command Dump");

  proto::DumpResponse* response = new proto::DumpResponse();
  workspace_.GetResponse().set_allocated_dump_response(response);

  // Retrieve apks for this package.
  auto apks_path = RetrieveApks(packageName_);
  if (apks_path.size() == 0) {
    response->set_status(proto::DumpResponse::ERROR_PACKAGE_NOT_FOUND);
    ErrEvent("ApkRetriever did not return apks");
    return;
  }

  // Extract all apks.
  for (std::string& apkPath : apks_path) {
    Phase p2("processing APK");
    ApkArchive archive(apkPath);
    Dump dump = archive.ExtractMetadata();

    proto::ApkDump* apk_dump = response->add_dumps();
    apk_dump->set_absolute_path(apkPath);
    if (dump.cd != nullptr || dump.signature != nullptr) {
      std::string apkFilename = std::string(strrchr(apkPath.c_str(), '/') + 1);
      apk_dump->set_name(apkFilename);
    }
    if (dump.cd != nullptr) {
      apk_dump->set_allocated_cd(dump.cd.release());
    }
    if (dump.signature != nullptr) {
      apk_dump->set_allocated_signature(dump.signature.release());
    }
  }
  response->set_status(proto::DumpResponse::OK);
}

std::vector<std::string> DumpCommand::RetrieveApks(
    const std::string& package_name) {
  Phase p("retrieve_apk_path");
  std::vector<std::string> apks;
  // First try with cmd. It may fail since path capability was added to "cmd" in
  // Android P.
  CmdCommand cmd(workspace_);
  std::string error_output;
  cmd.GetAppApks(package_name, &apks, &error_output);
  if (apks.size() == 0) {
    // "cmd" likely failed. Try with PackageManager (pm)
    PackageManager pm(workspace_);
    pm.GetApks(package_name, &apks, &error_output);
  }
  return apks;
}

}  // namespace deploy
