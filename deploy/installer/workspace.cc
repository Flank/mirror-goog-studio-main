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

#include "workspace.h"

#include <fcntl.h>

namespace deploy {

namespace {
constexpr int kDirectoryMode = (S_IRWXG | S_IRWXU | S_IRWXO);
}

Workspace::Workspace(const std::string& executable_path)
    : executable_path_(executable_path), output_pipe_(dup(STDOUT_FILENO)) {
  base_ = RetrieveBase() + "/";

  // Create all directory that may be used.
  // Create the dump folder.
  dumps_ = base_ + "/dumps/";
  mkdir(dumps_.c_str(), kDirectoryMode);

  tmp_ = base_ + "/tmp/";
  mkdir(tmp_.c_str(), kDirectoryMode);

  // Close all file descriptor which could potentially mess up with
  // our protobuffer output and install a data sink instead.
  close(STDERR_FILENO);
  close(STDOUT_FILENO);
  open("/dev/null", 0);
  open("/dev/null", 0);
}

void Workspace::ClearDirectory(const char* dirname) const noexcept {
  DIR* dir;
  struct dirent* entry;
  char path[PATH_MAX];
  dir = opendir(dirname);
  if (dir == nullptr) {
    return;
  }

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

std::string Workspace::RetrieveBase() const noexcept {
  // Retrieves the base folder which is expected to be ".studio" somewhere in
  // the path.e.g: /data/local/tmp/.studio/bin base is /data/local/tmp/.studio.
  char* directory_cursor = const_cast<char*>(executable_path_.c_str());
  // Search for ".studio" folder.
  while (directory_cursor[0] != '/' || directory_cursor[1] != 0) {
    directory_cursor = dirname(directory_cursor);
    if (!strcmp(kBasename, basename(directory_cursor))) {
      return directory_cursor;
    }
  }
  std::cerr << "Unable to find '" << kBasename << "' base folder in '"
            << executable_path_ << "'" << std::endl;
  return "";
}
void Workspace::SendResponse() const noexcept {
  std::string responseString;
  response_.SerializeToString(&responseString);
  output_pipe_.Write(responseString);
}

}  // namespace deploy