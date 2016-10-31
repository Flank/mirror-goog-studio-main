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
#include "perfd/cpu/procfs_files.h"

#include <sstream>  // for std::ostringstream

using std::string;

namespace {

// Absolute path of system stat file.
const char* const kProcStatFilename = "/proc/stat";

}  // namespace

namespace profiler {

string ProcfsFiles::GetSystemStatFilePath() const { return kProcStatFilename; }

string ProcfsFiles::GetProcessStatFilePath(int32_t pid) const {
  // TODO: Use std::to_string() after we use libc++. NDK doesn't support itoa().
  std::ostringstream os;
  os << "/proc/" << pid << "/stat";
  return os.str();
}

}  // namespace profiler
