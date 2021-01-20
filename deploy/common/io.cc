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
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"

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

static bool DirectoryExists(const char* dir_path) {
  struct stat sb;
  return (stat(dir_path, &sb) == 0 && S_ISDIR(sb.st_mode));
}

bool IO::mkpath(const std::string& p, mode_t mode) {
  std::string resolvedPath = ResolvePath(p);

  char path[resolvedPath.size() + 1];
  strcpy(path, resolvedPath.c_str());
  if (path[resolvedPath.size() - 1] == '/') {
    path[resolvedPath.size() - 1] = 0;
  }

  if (DirectoryExists(path)) {
    return true;
  }

  size_t path_size = strlen(path);
  for (size_t i = 0; i < path_size; i++) {
    if (path[i] != '/' || i == 0) {
      continue;
    }

    char c = path[i];  // Save character for patching/restoring
    path[i] = '\0';    // Patch / with string termination.

    if (!DirectoryExists(path)) {
      int error = ::mkdir(path, mode);
      if (error) {
        std::string err_msg = "Unable to create '"_s + path + "'";
        err_msg += " reason:'"_s + strerror(errno) + "'";
        ErrEvent(err_msg);
        return false;
      }
    }
    path[i] = c;
  }

  // The last directory was not created in the loop.
  int error = ::mkdir(path, mode);
  if (error) {
    std::string err_msg = "Unable to create '"_s + path + "'";
    err_msg += " reason:'"_s + strerror(errno) + "'";
    ErrEvent(err_msg);
  }

  if (!DirectoryExists(path)) {
    std::string err_msg = "Unable to create '"_s + path + "'";
    err_msg += " reason:'"_s + strerror(errno) + "'";
    ErrEvent(err_msg);
    return false;
  }

  return true;
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

int IO::rmdir(const std::string& pathname) {
  std::string path = ResolvePath(pathname);
  DIR* dir = ::opendir(path.c_str());
  if (dir == nullptr) {
    return 1;
  }

  dirent* ent;
  while ((ent = readdir(dir)) != nullptr) {
    if (ent->d_type == DT_REG) {
      std::string full_path = path + "/" + ent->d_name;
      int status = ::unlink(full_path.c_str());
      if (status) {
        closedir(dir);
        return status;
      }
    }
    // No use case for recursive rmdir yet.
  }
  closedir(dir);
  return ::rmdir(path.c_str());
}

}  // namespace deploy
