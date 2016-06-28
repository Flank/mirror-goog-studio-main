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
#include "dir.h"
#include "utils/fs/file_system.h"

namespace profiler {

Dir::Dir(FileSystem *fs, const std::string &path) : Path(fs, path) {}

bool Dir::Exists() const { return fs_->disk()->HasDir(path_); }

bool Dir::IsAncestorOf(const Path &path) const {
  return path.path().compare(0, path_.length(), path_) == 0;
}

std::shared_ptr<Dir> Dir::GetDir(const std::string &path) {
  return DoGetDir(path);
}

const std::shared_ptr<Dir> Dir::GetDir(const std::string &path) const {
  return DoGetDir(path);
}

std::shared_ptr<Dir> Dir::NewDir(const std::string &path) {
  auto dir = GetDir(path);
  dir->Delete();
  dir->Create();
  return dir;
}

std::shared_ptr<Dir> Dir::GetOrNewDir(const std::string &path) {
  auto dir = GetDir(path);
  if (!dir->Exists()) {
    dir->Create();
  }
  return dir;
}

std::shared_ptr<File> Dir::GetFile(const std::string &path) {
  return DoGetFile(path);
}

const std::shared_ptr<File> Dir::GetFile(const std::string &path) const {
  return DoGetFile(path);
}

std::shared_ptr<File> Dir::NewFile(const std::string &path) {
  auto file = GetFile(path);
  file->Delete();
  file->Create();
  return file;
}

std::shared_ptr<File> Dir::GetOrNewFile(const std::string &path) {
  auto file = GetFile(path);
  if (!file->Exists()) {
    file->Create();
  }
  return file;
}

std::shared_ptr<Dir> Dir::DoGetDir(const std::string &path) const {
  return fs_->DirFor(path_ + "/" + path);
}

std::shared_ptr<File> Dir::DoGetFile(const std::string &path) const {
  return fs_->FileFor(path_ + "/" + path);
}

void Dir::Walk(std::function<void(const PathStat &)> callback) const {
  fs_->disk()->WalkDir(path_, callback);
}

bool Dir::HandleCreate() { return fs_->disk()->NewDir(path_); }

bool Dir::HandleDelete() { return fs_->disk()->RmDir(path_); }
}