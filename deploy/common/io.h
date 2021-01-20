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

#ifndef COMMON_IO_H
#define COMMON_IO_H

#include <dirent.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <string>

// In order to allow compatibility with the FakeDevice testing environment, when
// building for non-Android platforms, we redefine these syscalls to be
// intrinsically rooted at the global test root directory.
//
// This is a specific class rather than a direct override of the syscalls, in
// order to make the abstraction clear when reading code.

namespace deploy {
class IO {
 public:
  // Resolves a filesystem path against the test root, if one is currently
  // configured.
  //
  // In a FakeDevice test context, the path "/some/path/here" will be resolved
  // to "/tmp/storageXXX/some/path/here".
  //
  // This method is public for the rare cases where the raw root-aware path
  // needs to be used, such as when passing paths to JVMTI.
  static std::string ResolvePath(const std::string& path);

  static int access(const std::string& pathname, int mode);
  static int creat(const std::string& pathname, mode_t mode);
  static FILE* fopen(const std::string& filename, const std::string& mode);
  static int stat(const std::string& pathname, struct stat* statbuf);
  static int chmod(const std::string& pathname, mode_t mode);
  static int mkdir(const std::string& pathname, mode_t mode);
  static bool mkpath(const std::string& pathname, mode_t mode);
  static int open(const std::string& pathname, int flags);
  static int open(const std::string& pathname, int flags, mode_t mode);
  static DIR* opendir(const std::string& name);
  static int unlink(const std::string& pathname);

  // remove a directory and its content NOT recursively (no need for it yet)
  static int rmdir(const std::string& pathname);
};

}  // namespace deploy

#endif
