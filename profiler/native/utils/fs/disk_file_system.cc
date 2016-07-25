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
#include "utils/fs/disk_file_system.h"

#include <fts.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utime.h>
#include <memory>

namespace profiler {

using std::function;
using std::string;
using std::unique_ptr;

DiskFileSystem::~DiskFileSystem() {
  for (auto it = open_files_.begin(); it != open_files_.end(); it++) {
    fclose(it->second);
  }
}

bool DiskFileSystem::HasDir(const std::string &dpath) const {
  struct stat s;
  int result = stat(dpath.c_str(), &s);
  if (result == 0) {
    return S_ISDIR(s.st_mode) != 0;
  } else {
    return false;
  }
}

bool DiskFileSystem::HasFile(const string &fpath) const {
  struct stat s;
  int result = stat(fpath.c_str(), &s);
  if (result == 0) {
    return S_ISREG(s.st_mode) != 0;
  } else {
    return false;
  }
}

bool DiskFileSystem::CreateDir(const string &dpath) {
  // TODO: Restrictive permissions should be good enough for now, but consider
  // allowing this to be configurable
  return mkdir(dpath.c_str(), 0700) == 0;
}

bool DiskFileSystem::CreateFile(const string &fpath) {
  FILE *file = fopen(fpath.c_str(), "wb");
  if (file != nullptr) {
    fclose(file);
    return true;
  }
  return false;
}

int32_t DiskFileSystem::GetModificationAge(const string &fpath) const {
  struct stat s;
  int result = stat(fpath.c_str(), &s);
  if (result == 0) {
    time_t now;
    time(&now);
    return difftime(now, s.st_mtime);
  } else {
    return 0;
  }
}

void DiskFileSystem::Touch(const string &path) { utime(path.c_str(), NULL); }

void DiskFileSystem::WalkDir(const string &dpath,
                             function<void(const PathStat &)> callback,
                             int32_t max_depth) const {
  unique_ptr<char[]> root_path(new char[dpath.length() + 1]);
  strcpy(root_path.get(), dpath.c_str());
  char *dir_args[2];
  dir_args[0] = root_path.get();
  dir_args[1] = nullptr;
  int open_options = FTS_PHYSICAL | FTS_NOCHDIR;
  FTS *fts = nullptr;
  if ((fts = fts_open(dir_args, open_options, nullptr)) != nullptr) {
    while (auto f = fts_read(fts)) {
      if (f->fts_level == max_depth) {
        fts_set(fts, f, FTS_SKIP);
      }
      bool valid = false;
      PathStat::Type type;
      const char *fts_path = f->fts_path;
      switch (f->fts_info) {
        case FTS_DP: {
          if (dpath != f->fts_path) {
            type = PathStat::Type::DIR;
            valid = true;
          }
        } break;
        case FTS_F: {
          type = PathStat::Type::FILE;
          valid = true;
        } break;
      }
      if (valid) {
        string full_path(fts_path);
        callback(
            PathStat(type, dpath, full_path, GetModificationAge(full_path)));
      }
    }

    fts_close(fts);
  }
}

string DiskFileSystem::GetFileContents(const string &fpath) const {
  FILE *file = fopen(fpath.c_str(), "rb");
  if (file == nullptr) {
    return "";
  }

  fseek(file, 0, SEEK_END);
  size_t size = ftell(file);
  rewind(file);

  unique_ptr<char> buffer(new char[size]);
  fread(buffer.get(), sizeof(char), size, file);
  string contents(buffer.get());

  return contents;
}

bool DiskFileSystem::MoveFile(const string &fpath_from,
                              const string &fpath_to) {
  return rename(fpath_from.c_str(), fpath_to.c_str());
}

bool DiskFileSystem::IsOpenForWrite(const string &fpath) const {
  return open_files_.find(fpath) != open_files_.end();
}

void DiskFileSystem::OpenForWrite(const string &fpath) {
  FILE *file = fopen(fpath.c_str(), "ab");
  if (file != nullptr) {
    open_files_[fpath] = file;
  }
}

bool DiskFileSystem::Append(const string &fpath, const string &str) {
  auto it = open_files_.find(fpath);
  if (it != open_files_.end()) {
    FILE *file = it->second;
    fwrite(str.c_str(), sizeof(char), str.length(), file);
    return true;
  }
  return false;
}

void DiskFileSystem::Close(const string &fpath) {
  auto it = open_files_.find(fpath);
  if (it != open_files_.end()) {
    FILE *file = it->second;
    fclose(file);
    open_files_.erase(it);
  }
}

bool DiskFileSystem::DeleteDir(const string &dpath) {
  WalkDir(dpath,
          [this](const PathStat &pstat) { remove(pstat.full_path().c_str()); },
          INT32_MAX);
  return remove(dpath.c_str());
}

bool DiskFileSystem::DeleteFile(const string &fpath) {
  Close(fpath);
  return remove(fpath.c_str()) == 0;
}
}  // namespace profiler
