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
#include "path.h"

#include "utils/fs/dir.h"
#include "utils/fs/file_system.h"

namespace profiler {

std::string Path::Standardize(const std::string &path) {
  std::string path_final = path;
  if (path_final[0] != '/') {
    path_final.insert(0, "/");
  }

  // Remove the last slash (as long as path is not "/")
  if (path.length() > 1 && path.back() == '/') {
    path_final = path_final.substr(0, path_final.length() - 1);
  }

  // Remove any redundant slashes, e.g. 'a/////b' -> 'a/b'
  std::string::size_type pos = 0;
  while ((pos = path_final.find("//", pos)) != std::string::npos) {
    path_final.replace(pos, 2, "/");
  }
  return path_final;
}

std::string Path::StripLast(const std::string &path) {
  std::size_t last_slash = path.find_last_of("/");
  if (last_slash > 0) {
    return path.substr(0, last_slash);
  } else {
    return "/";
  }
}

Path::Path(FileSystem *fs, const std::string &path)
    : fs_(fs), path_(Standardize(path)) {
  std::size_t last_slash = path.find_last_of("/");
  name_ = path.substr(last_slash + 1);
}

bool Path::Create() {
  if (fs_->disk()->HasDir(path_) || fs_->disk()->HasFile(path_)) {
    return false;
  }

  auto root = fs_->root();
  if (this == root.get()) {
    if (!root->Exists()) {
      return fs_->disk()->NewDir(root->path());
    } else {
      return false;
    }
  }

  if (!root->IsAncestorOf(*this) || !root->Exists()) {
    return false;
  }
  if (path_.find("..") != std::string::npos) {
    return false;  // No relative paths allowed!
  }

  if (CreateDirsRecursively(StripLast(path_))) {
    return HandleCreate();
  } else {
    return false;
  }
}

bool Path::CreateDirsRecursively(const std::string &path) {
  if (path.length() == 1) {
    return true;  // path == "/"
  }

  auto d = fs_->DirFor(path);
  if (d->Exists()) {
    return true;
  }

  if (CreateDirsRecursively(StripLast(path))) {
    return d->HandleCreate();
  } else {
    return false;
  }
}

bool Path::Delete() {
  if (!Exists()) {
    return false;
  }

  return HandleDelete();
}

const std::shared_ptr<Dir> Path::Up() const { return DoUp(); }

std::shared_ptr<Dir> Path::Up() { return DoUp(); }

std::shared_ptr<Dir> Path::DoUp() const {
  auto root = fs_->root();
  if (path_ == root->path()) {
    return root;  // Can't go above root
  }

  return fs_->DirFor(StripLast(path_));
}
}