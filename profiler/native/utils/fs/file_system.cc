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
#include "file_system.h"

namespace profiler {

FileSystem::FileSystem(const std::string &root_path)
    : FileSystem(std::make_shared<CDisk>(), root_path) {}

FileSystem::FileSystem(std::shared_ptr<Disk> disk, const std::string &root_path)
    : disk_(disk) {
  root_ = DirFor(root_path);
  root_->CreateDirsRecursively(root_path);
}

std::shared_ptr<Dir> FileSystem::DirFor(const std::string &path) {
  std::string path_standard = Path::Standardize(path);

  // Can't use make_shared; must create Dir directly because of friend access
  return std::shared_ptr<Dir>(new Dir(this, path_standard));
}

std::shared_ptr<File> FileSystem::FileFor(const std::string &path) {
  std::string path_standard = Path::Standardize(path);

  // Can't use make_shared; must create File directly because of friend access
  return std::shared_ptr<File>(new File(this, path_standard));
}

}  // namespace profiler
