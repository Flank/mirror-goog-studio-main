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
#include "file.h"

#include "utils/fs/file_system.h"

namespace profiler {

File::File(FileSystem *fs, const std::string &path) : Path(fs, path) {}

File::~File() { Close(); }

bool File::Exists() const { return fs_->disk()->HasFile(path_); }

void File::Touch() {
  if (Exists()) {
    fs_->disk()->Touch(path_);
  }
}

std::string File::Contents() const {
  if (Exists() && !IsOpenForWrite()) {
    return fs_->disk()->GetFileContents(path_);
  } else {
    return "";
  }
}

bool File::MoveContentsTo(std::shared_ptr<File> dest) {
  if (!Exists()) {
    return false;
  }
  if (IsOpenForWrite()) {
    return false;
  }
  if (dest->IsOpenForWrite()) {
    return false;
  }
  if (path_ == dest->path()) {
    return true;
  }

  dest->Delete();
  return fs_->disk()->MoveFile(path_, dest->path());
}

bool File::IsOpenForWrite() const { return fs_->disk()->IsOpenForWrite(path_); }

void File::OpenForWrite() {
  if (Exists()) {
    fs_->disk()->OpenForWrite(path_);
  }
}

void File::Append(const std::string &str) {
  if (IsOpenForWrite()) {
    fs_->disk()->Append(path_, str);
  }
}

void File::Close() {
  if (IsOpenForWrite()) {
    fs_->disk()->Close(path_);
  }
}

bool File::HandleCreate() { return fs_->disk()->NewFile(path_); }

bool File::HandleDelete() { return fs_->disk()->RmFile(path_); }
}