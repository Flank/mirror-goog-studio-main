/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef UTILS_FS_DIR_H_
#define UTILS_FS_DIR_H_

#include <functional>
#include <memory>
#include <string>

#include "utils/fs/disk.h"
#include "utils/fs/path.h"

namespace profiler {

class File;
class FileSystem;

// A handle to a directory location. The directory may or may not exist; use
// |Exists| to check and |Create| to actually create it.
class Dir final : public Path {
  friend FileSystem;  // So FileSystem can create Dir
  friend Path;        // So Path can call HandleCreate directly on any Dir

 public:
  virtual ~Dir() = default;

  // Check to see if this directory already exists
  bool Exists() const override;

  // Returns true if this directory is the ancestor of the target path. This
  // also returns true if the path refers to this directory itself.
  bool IsAncestorOf(const Path &path) const;

  // Fetch a directory handle for the specified path.
  std::shared_ptr<Dir> GetDir(const std::string &path);
  const std::shared_ptr<Dir> GetDir(const std::string &path) const;

  // Shortcut for calling |GetDir| followed by |Create|. This will overwrite
  // an existing directory (but not an existing file).
  std::shared_ptr<Dir> NewDir(const std::string &path);

  // Shortcut for calling |GetDir| followed by |Dir::Create| if the directory
  // doesn't already exists.
  std::shared_ptr<Dir> GetOrNewDir(const std::string &path);

  // Fetch a file handle for the specified path.
  std::shared_ptr<File> GetFile(const std::string &path);
  const std::shared_ptr<File> GetFile(const std::string &path) const;

  // Shortcut for calling |GetFile| followed by |File::Create|. This will
  // overwrite an existing file (but not an existing directory).
  std::shared_ptr<File> NewFile(const std::string &path);

  // Shortcut for calling |GetFile| followed by |File::Create| if the file
  // doesn't already exists.
  std::shared_ptr<File> GetOrNewFile(const std::string &path);

  // Walk each file in this directory, triggering a callback for each file
  // visited. The callback will be triggered in an order where the paths can
  // safely be deleted (i.e. children first).
  void Walk(std::function<void(const PathStat &)>) const;

 protected:
  // Don't create directly. Use |GetDir| or |NewDir| from a parent directory
  // instead.
  Dir(FileSystem *fs, const std::string &path);

  bool HandleCreate() override;
  bool HandleDelete() override;

 private:
  std::shared_ptr<Dir> DoGetDir(const std::string &path) const;
  std::shared_ptr<File> DoGetFile(const std::string &path) const;
};

}  // namespace profiler

#endif  // UTILS_FS_DIR_H_
