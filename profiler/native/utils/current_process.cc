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

#include "utils/current_process.h"

#include <libgen.h>
#include <limits.h>
#include <stdlib.h>
#include <string>

using std::string;

namespace profiler {

namespace {

const char* const kProcSelfExe = "/proc/self/exe";

// Returns the absolute path of the calling process. Ends with a '/'. Returns an
// empty string on failure.
string GetExeDir() {
  // PATH_MAX is the maximum number of bytes in a pathname, including the
  // terminating null character.
  char buffer[PATH_MAX];
  // realpath() returns the canonicalized absolute pathname.
  char* real = realpath(kProcSelfExe, buffer);
  if (real == nullptr) {
    return string{};  // Returns an empty string on failure.
  }
  return string{dirname(buffer)} + "/";
}

}  // namespace

CurrentProcess::CurrentProcess() { dir_ = GetExeDir(); }

CurrentProcess* CurrentProcess::Instance() {
  static CurrentProcess* instance = new CurrentProcess();
  return instance;
}

}  // namespace profiler
