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
#ifndef UTILS_FS_FILE_SYSTEM_H_
#define UTILS_FS_FILE_SYSTEM_H_

#include <functional>
#include <map>
#include <memory>
#include <string>

#include "utils/fs/dir.h"
#include "utils/fs/disk.h"
#include "utils/fs/file.h"

namespace profiler {

// A mockable file system providing basic file operations
// Example:
//  FileSystem fs("/tmp/myapp/");
//
//  // Reading files
//  auto settings = fs.root()->GetFile(".appsettings");
//  assert(settings->Exists());
//  auto contents = settings->Contents();
//  ...
//
//  // Working with directories
//  auto cache = fs.root()->GetDir("cache")->Delete();
//  fs.root()->NewDir("cache/images");
//  fs.root()->NewDir("cache/movies");
//  // Creating subdirs should recreate parent cache dir
//  assert(cache->Exists());
//
//  // Editing files
//  auto cache_lock = cache->NewFile("cache.lock");
//  ... write files into the cache ...
//  cache_lock->Delete();
//  auto log = fs.root()->GetFile("logs/cache.log");
//  *log << "Cache modified at " << clock.GetCurrentTime() << endl;
//
// The FileSystem class is NOT thread safe so be careful when modifying
// directories and files across threads.
class FileSystem final {
  // Expose access to |DirFor|, |FileFor|, and |disk| methods
  friend File;
  friend Dir;
  friend Path;

 public:
  explicit FileSystem(const std::string &root_path);
  FileSystem(std::shared_ptr<Disk> disk, const std::string &root_path);

  ~FileSystem() = default;

  std::shared_ptr<Dir> root() { return root_; }
  const std::shared_ptr<Dir> root() const { return root_; }

 private:
  std::shared_ptr<Disk> disk() { return disk_; }
  const std::shared_ptr<Disk> disk() const { return disk_; }

  // Returns a directory handle for the specified path
  std::shared_ptr<Dir> DirFor(const std::string &abs_path);
  // Returns a file handle for the specified path
  std::shared_ptr<File> FileFor(const std::string &abs_path);

  std::shared_ptr<Disk> disk_;
  std::shared_ptr<Dir> root_;
};

}  // namespace profiler

#endif  // UTILS_FS_FILE_SYSTEM_H_
