/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "tools/base/deploy/common/io.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

std::string IO::ResolvePath(const std::string& path) {
  // Only apply the prefix to absolute paths.
  if (!Env::root().empty() && path.size() > 0 && path[0] == '/') {
    return Env::root() + path;
  }
  return path;
}

int IO::access(const std::string& pathname, int mode) {
  return ::access(ResolvePath(pathname).c_str(), mode);
}

int IO::creat(const std::string& pathname, mode_t mode) {
  return ::creat(ResolvePath(pathname).c_str(), mode);
}

FILE* IO::fopen(const std::string& filename, const std::string& mode) {
  return ::fopen(ResolvePath(filename).c_str(), mode.c_str());
}

int IO::stat(const std::string& pathname, struct stat* statbuf) {
  int ret = ::stat(ResolvePath(pathname).c_str(), statbuf);
#ifdef __ANDROID__
  return ret;
#else
  // In tests contexts, we need to do some trickery re: /proc entries, because
  // we use Android-specific UID conventions in Apply Changes.
  if (ret != 0 || pathname.find("/proc") != 0) {
    return ret;
  }

  FILE* uid = IO::fopen(pathname + "/.uid", "r");
  if (uid == nullptr) {
    Log::E("Cannot fake-stat %s", pathname.c_str());
    return 1;
  }
  fscanf(uid, "%d", &statbuf->st_uid);
  fclose(uid);
  return 0;
#endif
}

int IO::chmod(const std::string& pathname, mode_t mode) {
  return ::chmod(ResolvePath(pathname).c_str(), mode);
}

int IO::mkdir(const std::string& pathname, mode_t mode) {
  return ::mkdir(ResolvePath(pathname).c_str(), mode);
}

int IO::open(const std::string& pathname, int flags) {
  return ::open(ResolvePath(pathname).c_str(), flags);
}

int IO::open(const std::string& pathname, int flags, mode_t mode) {
  return ::open(ResolvePath(pathname).c_str(), flags, mode);
}

DIR* IO::opendir(const std::string& name) {
  return ::opendir(ResolvePath(name).c_str());
}

int IO::unlink(const std::string& pathname) {
  return ::unlink(ResolvePath(pathname).c_str());
}

}  // namespace deploy