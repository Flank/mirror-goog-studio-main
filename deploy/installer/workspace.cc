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

#include "tools/base/deploy/common/utils.h"

// Defined in main.cc
std::string GetVersion();

namespace deploy {

namespace {
constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
}

Workspace::Workspace(const std::string& executable_path, Executor* executor)
    : executor_(executor), output_pipe_(dup(STDOUT_FILENO)) {
  base_ = kBasedir;
  tmp_ = base_ + "tmp-" + GetVersion() + "/";
}

void Workspace::Init() noexcept {
  // Create all directory that may be used.
  std::string dir = GetRoot() + tmp_;
  mkdir(dir.c_str(), kDirectoryMode);

  // Close all file descriptor which could potentially mess up with
  // our protobuffer output and install a data sink instead.
  close(STDERR_FILENO);
  close(STDOUT_FILENO);
  open("/dev/null", O_WRONLY);
  open("/dev/null", O_WRONLY);
}

void Workspace::SendResponse() noexcept {
  std::unique_ptr<std::vector<Event>> events = ConsumeEvents();
  for (Event& event : *events) {
    ConvertEventToProtoEvent(event, response_.add_events());
  }
  std::string responseString;
  response_.SerializeToString(&responseString);
  output_pipe_.Write(responseString);
}

}  // namespace deploy