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

// Given a root directory path, walk all of its children and file callbacks for
// each entry visited. Children files will be walked BEFORE their parent
// directory. The root directory will NOT be included in the triggered
// callbacks.
//
// The callbacks will be triggered with the absolute path to the file or
// directory visited.
void Walk(const std::string &dpath_root,
          std::function<void(const char *)> file_callback,
          std::function<void(const char *)> dir_callback) {
  FTS *fts;

  char **dir_args = new char *[2];
  dir_args[0] = new char[dpath_root.length() + 1];
  strcpy(dir_args[0], dpath_root.c_str());
  dir_args[1] = NULL;
  int open_options = FTS_PHYSICAL | FTS_NOCHDIR;
  if ((fts = fts_open(dir_args, open_options, NULL)) != NULL) {
    while (auto f = fts_read(fts)) {
      switch (f->fts_info) {
        case FTS_DP: {
          if (dpath_root != f->fts_path) {
            dir_callback(f->fts_path);
          }
        } break;
        case FTS_F: {
          file_callback(f->fts_path);
        } break;
      }
    }

    fts_close(fts);
  }

  delete[] dir_args[0];
  delete[] dir_args;
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

int32_t CDisk::ModifyAge(const std::string &fpath) const {
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

void CDisk::Touch(const std::string &fpath) { utime(fpath.c_str(), NULL); }

void CDisk::WalkFiles(const std::string &dpath,
                      std::function<void(const FileStat &)> callback) {
  Walk(dpath,
       [this, &dpath, callback](const char *path) {
         std::string full_path(path);
         std::string rel_path = full_path.substr(dpath.length() + 1);

         FileStat fstat(rel_path, ModifyAge(full_path));
         callback(fstat);
       },
       [](const char *path) {});
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
  Walk(dpath, [this](const char *path) { RmFile(path); },
       [](const char *path) { remove(path); });
  return remove(dpath.c_str());
}

bool CDisk::RmFile(const std::string &fpath) {
  Close(fpath);
  return remove(fpath.c_str()) == 0;
}
}