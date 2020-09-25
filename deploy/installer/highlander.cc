/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <fcntl.h>
#include <signal.h>
#include <unistd.h>

#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/highlander.h"

namespace deploy {

// Highlander ensures that only one instance of installer runs on a
// a device at any time.

// When it starts, the installer deamon writes its pid to a file in a
// specific directory. Before doing so, it checks for any file already
// present in this folder to signal kill(2) any potentially other
// pids.

Highlander::Highlander(const Workspace& workspace) {
  // There can be only one...
  TerminateOtherInstances(workspace);
  WritePid(workspace);
}

Highlander::~Highlander() { IO::unlink(pid_file_path_); }

void Highlander::TerminateOtherInstances(const Workspace& workspace) {
  const std::string pids_folder = workspace.GetInstallerdPidsFolder();
  DIR* dir = IO::opendir(pids_folder);
  if (dir == nullptr) {
    return;
  }

  dirent* pid_file;
  while ((pid_file = readdir(dir)) != nullptr) {
    if (pid_file->d_type != DT_REG) {
      continue;
    }

    // Send kill signal
    int64_t pid = atoi(pid_file->d_name);
    kill(pid, SIGKILL);

    std::string pid_absolute_path = pids_folder + to_string(pid_file->d_name);
    IO::unlink(pid_absolute_path);
  }
  closedir(dir);
}

void Highlander::WritePid(const Workspace& workspace) {
  pid_file_path_ = workspace.GetInstallerdPidsFolder() + to_string(getpid());
  int fd = IO::open(pid_file_path_.c_str(), O_CREAT | O_RDWR, 0600);
  close(fd);
}

}  // namespace deploy
