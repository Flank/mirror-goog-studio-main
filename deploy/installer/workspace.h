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

#include <dirent.h>
#include <libgen.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <iostream>
#include <string>

#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class Workspace {
 public:
  Workspace(const std::string& executable_path);

  bool Valid() const { return base_ != ""; }

  std::string GetBase() const noexcept { return base_; }

  const std::string GetAppsFolder() const noexcept { return apps_; }

  const std::string GetBinFolder() const noexcept { return base_ + "/bin"; }

  const std::string GetTmpFolder() const noexcept { return tmp_; }

  proto::InstallerResponse& GetResponse() noexcept { return response_; }

  void SendResponse() const noexcept;

 private:
  std::string RetrieveBase() const noexcept;
  static constexpr auto kBasename = ".studio";
  std::string executable_path_;
  std::string base_;
  std::string apps_;
  std::string tmp_;
  deploy::MessagePipeWrapper output_pipe_;
  proto::InstallerResponse response_;
};

}  // namespace deploy

#endif