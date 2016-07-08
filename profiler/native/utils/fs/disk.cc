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
#include "disk.h"

#include <fts.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utime.h>
#include <memory>

namespace {

using profiler::Disk;
using profiler::PathStat;
using std::function;
using std::string;
using std::unique_ptr;

// Use the FTS (file traversal) API to walk the contents of the specified
// directory path. Triggers the callback in child-first order.
void FtsWalk(const Disk *disk, const string &dpath,
             function<void(const PathStat &)> callback) {
  unique_ptr<char[]> root_path(new char[dpath.length() + 1]);
  strcpy(root_path.get(), dpath.c_str());
  char *dir_args[2];
  dir_args[0] = root_path.get();
  dir_args[1] = nullptr;
  int open_options = FTS_PHYSICAL | FTS_NOCHDIR;
  FTS *fts = nullptr;
  if ((fts = fts_open(dir_args, open_options, nullptr)) != nullptr) {
    while (auto f = fts_read(fts)) {
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
        std::string full_path(fts_path);
        callback(PathStat(type, dpath, full_path,
                          disk->GetModificationAge(full_path)));
      }
    }

    fts_close(fts);
  }
}
}

namespace profiler {

CDisk::~CDisk() {
  for (auto it = open_files_.begin(); it != open_files_.end(); it++) {
    fclose(it->second);
  }
}

bool CDisk::HasDir(const std::string &dpath) const {
  struct stat s;
  int result = stat(dpath.c_str(), &s);
  if (result == 0) {
    return S_ISDIR(s.st_mode) != 0;
  } else {
    return false;
  }
}

bool CDisk::HasFile(const std::string &fpath) const {
  struct stat s;
  int result = stat(fpath.c_str(), &s);
  if (result == 0) {
    return S_ISREG(s.st_mode) != 0;
  } else {
    return false;
  }
}

bool CDisk::NewDir(const std::string &dpath) {
  // TODO: Restrictive permissions should be good enough for now, but consider
  // allowing this to be configurable
  return mkdir(dpath.c_str(), 0700) == 0;
}

bool CDisk::NewFile(const std::string &fpath) {
  FILE *file = fopen(fpath.c_str(), "wb");
  if (file != nullptr) {
    fclose(file);
    return true;
  }
  return false;
}

int32_t CDisk::GetModificationAge(const std::string &fpath) const {
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

void CDisk::Touch(const std::string &path) { utime(path.c_str(), NULL); }

void CDisk::WalkDir(const std::string &dpath,
                    std::function<void(const PathStat &)> callback) const {
  return FtsWalk(this, dpath, callback);
}

std::string CDisk::GetFileContents(const std::string &fpath) const {
  FILE *file = fopen(fpath.c_str(), "rb");
  if (file == nullptr) {
    return "";
  }

  fseek(file, 0, SEEK_END);
  size_t size = ftell(file);
  rewind(file);

  std::unique_ptr<char> buffer(new char[size]);
  fread(buffer.get(), sizeof(char), size, file);
  std::string contents(buffer.get());

  return contents;
}

bool CDisk::MoveFile(const std::string &fpath_from,
                     const std::string &fpath_to) {
  return rename(fpath_from.c_str(), fpath_to.c_str());
}

bool CDisk::IsOpenForWrite(const std::string &fpath) const {
  return open_files_.find(fpath) != open_files_.end();
}

void CDisk::OpenForWrite(const std::string &fpath) {
  FILE *file = fopen(fpath.c_str(), "ab");
  if (file != nullptr) {
    open_files_[fpath] = file;
  }
}

bool CDisk::Append(const std::string &fpath, const std::string &str) {
  auto it = open_files_.find(fpath);
  if (it != open_files_.end()) {
    FILE *file = it->second;
    fwrite(str.c_str(), sizeof(char), str.length(), file);
    return true;
  }
  return false;
}

void CDisk::Close(const std::string &fpath) {
  auto it = open_files_.find(fpath);
  if (it != open_files_.end()) {
    FILE *file = it->second;
    fclose(file);
    open_files_.erase(it);
  }
}

bool CDisk::RmDir(const std::string &dpath) {
  FtsWalk(this, dpath,
          [this](const PathStat &pstat) { remove(pstat.full_path().c_str()); });
  return remove(dpath.c_str());
}

bool CDisk::RmFile(const std::string &fpath) {
  Close(fpath);
  return remove(fpath.c_str()) == 0;
}
}