/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "tools/base/deploy/installer/overlay/overlay.h"

#include <fcntl.h>
#include <ftw.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <cstdlib>
#include <deque>
#include <string>

#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"

namespace deploy {

const std::string kIdFile = "id";

bool Overlay::Exists(const std::string& overlay_folder, const std::string& id) {
  const std::string id_file = overlay_folder + kIdFile;
  std::string content;
  if (!deploy::ReadFile(id_file.c_str(), &content)) {
    ErrEvent("Checking for overlay id '" + id +
             "' but overlay has no readable id file");
    return false;
  }

  if (id != content) {
    ErrEvent("Checking for overlay id '" + id +
             "' but existing overlay id is '" + content + "'");
    return false;
  }

  return true;
}

bool Overlay::Open() {
  if (is_open_) {
    return true;
  }

  if (IO::access(overlay_folder_, F_OK) != 0) {
    // If overlay directory does not already exist, create one.
    if (!IO::mkpath(overlay_folder_, S_IRWXU)) {
      ErrEvent("Could not create overlay folder at '" + overlay_folder_ +
               "': " + strerror(errno));
      return false;
    }
  } else {
    // If an overlay directory already exists, delete the id file to mark it as
    // dirty. We cannot use DeleteFile() here, because the overlay isn't open
    // yet.
    const std::string id_file = overlay_folder_ + kIdFile;
    if (IO::unlink(id_file) != 0) {
      ErrEvent("Could not remove id file to open overlay: "_s +
               strerror(errno));
      return false;
    }
  }

  is_open_ = true;
  return true;
}

bool Overlay::WriteFile(const std::string& path,
                        const std::string& content) const {
  if (!is_open_) {
    ErrEvent("Overlay must be opened before it can be modified");
    return false;
  }

  const std::string overlay_path = overlay_folder_ + path;

  // Maintain a stack of directories to create.
  std::deque<std::string> dirs;
  std::string dir = overlay_path.substr(0, overlay_path.find_last_of('/'));

  // Check each path in reverse order so we check the minimum number of paths.
  while (IO::access(dir, F_OK)) {
    std::string next = dir.substr(0, dir.find_last_of('/'));
    dirs.push_back(std::move(dir));
    dir = std::move(next);
  }

  // Create the directories that don't already exist.
  while (!dirs.empty()) {
    if (IO::mkdir(dirs.back(), S_IRWXU) < 0) {
      ErrEvent("Could not create directory at '" + dirs.back() +
               "': " + strerror(errno));
      return false;
    }
    dirs.pop_back();
  }

  if (!deploy::WriteFile(overlay_path, content)) {
    ErrEvent("Could not write file at '" + overlay_path + "'");
    return false;
  }

  return true;
}

bool Overlay::DeleteFile(const std::string& path) const {
  if (!is_open_) {
    ErrEvent("Overlay must be opened before it can be modified");
    return false;
  }

  const std::string overlay_path = overlay_folder_ + path;
  if (IO::unlink(overlay_path) != 0) {
    ErrEvent("Could not remove file '" + overlay_path +
             "': " + strerror(errno));
    return false;
  }

  return true;
}

bool Overlay::DeleteDirectory(const std::string& path) const {
  if (!is_open_) {
    ErrEvent("Overlay must be opened before it can be modified");
    return false;
  }

  if (IO::rmdir(path) != 0) {
    ErrEvent("Could not remove file '" + path + "': " + strerror(errno));
    return false;
  }

  return true;
}

bool Overlay::Commit() {
  if (!is_open_) {
    ErrEvent("Cannot commit an overlay that is not open for modification");
    return false;
  }

  if (!WriteFile(kIdFile, id_)) {
    return false;
  }

  is_open_ = false;
  return true;
}

}  // namespace deploy
