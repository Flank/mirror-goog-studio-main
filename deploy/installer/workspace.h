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

#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

class Workspace {
 public:
  Workspace(const std::string& executable_path, Executor* executor);

  bool Valid() const { return base_ != ""; }

  const std::string GetRoot() const noexcept { return root_; }

  void SetRoot(const std::string& root) { root_ = root; }

  const std::string GetBase() const noexcept { return base_; }

  const std::string GetTmpFolder() const noexcept { return tmp_; }

  Executor& GetExecutor() const noexcept { return *executor_; }

  void SetExecutor(Executor* executor) { executor_ = executor; }

  proto::InstallerResponse& GetResponse() noexcept { return response_; }

  void SendResponse() noexcept;

 private:
  static constexpr auto kBasename = ".studio";

  static std::string RetrieveBase(const std::string& path) noexcept;

  std::string base_;
  std::string tmp_;
  std::string root_;

  Executor* executor_;

  deploy::MessagePipeWrapper output_pipe_;
  proto::InstallerResponse response_;
};

}  // namespace deploy

#endif