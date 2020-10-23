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

#ifndef INSTALLER_WORKSPACE_H
#define INSTALLER_WORKSPACE_H

#include <iostream>
#include <string>

#include <dirent.h>
#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/installer/executor/executor.h"

namespace deploy {

class Workspace {
 public:
  Workspace(const std::string& version);

  const std::string GetVersion() const noexcept { return version_; }

  const std::string& GetPmPath() const noexcept { return pm_path_; }

  void SetPmPath(const std::string& path) { pm_path_ = path; }

  const std::string& GetCmdPath() const noexcept { return cmd_path_; }

  void SetCmdPath(const std::string& path) { cmd_path_ = path; }

  const std::string GetTmpFolder() const noexcept { return tmp_; }

  const std::string GetInstallerdPidsFolder() const noexcept {
    return pids_folder_;
  };

  const MessagePipeWrapper& GetOutput() const noexcept { return output_pipe_; }

  void Init() noexcept;

 private:
  const std::string version_;

  std::string pm_path_;
  std::string cmd_path_;

  std::string tmp_;
  std::string pids_folder_;

  deploy::MessagePipeWrapper output_pipe_;
};

}  // namespace deploy

#endif