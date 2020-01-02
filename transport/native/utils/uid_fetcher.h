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
#ifndef UTILS_UID_FETCHER_H_
#define UTILS_UID_FETCHER_H_

#include <cstdlib>
#include <string>

#include "file_reader.h"
#include "tokenizer.h"

namespace profiler {

// Contains utils for fetching a process's uid from it's pid.
class UidFetcher {
 public:
  // Returns -1 if the corresponding Uid can't be found. Note that this does a
  // file read operation to get the uid, and should not be called too frequently
  // unless necessary.
  static int GetUid(int pid);

  // Visible for testing
  static bool GetUidStringFromPidFile(std::string file_path,
                                      std::string *uid_result);

 private:
  UidFetcher() = delete;
};

}  // namespace profiler

#endif  // UTILS_UID_FETCHER_H_
