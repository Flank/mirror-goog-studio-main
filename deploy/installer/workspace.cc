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

#include "tools/base/deploy/installer/workspace.h"

#include <fcntl.h>

#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"

namespace deploy {

namespace {
constexpr const char* kBaseDir = "/data/local/tmp/.studio";
constexpr const char* kDefaultPmPath = "/system/bin/pm";
constexpr const char* kDefaultCmdPath = "/system/bin/cmd";
constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
}  // namespace

Workspace::Workspace(const std::string& version)
    : version_(version),
      pm_path_(kDefaultPmPath),
      cmd_path_(kDefaultCmdPath),
      output_pipe_(dup(STDOUT_FILENO)) {
  tmp_ = kBaseDir + "/tmp/"_s + version + "/";
  pids_folder_ = kBaseDir + "/ipids/"_s;
}

void Workspace::Init() noexcept {
  // Create all directory that may be used.
  IO::mkpath(tmp_, kDirectoryMode);
  IO::mkpath(pids_folder_, kDirectoryMode);

  // Close all file descriptor which could potentially mess up with
  // our protobuffer output and install a data sink instead.
  close(STDERR_FILENO);
  close(STDOUT_FILENO);
  open("/dev/null", O_WRONLY);
  open("/dev/null", O_WRONLY);
}

}  // namespace deploy