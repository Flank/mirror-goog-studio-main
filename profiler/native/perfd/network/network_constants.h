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
#ifndef PERFD_NETWORK_NETWORK_CONSTANTS_H_
#define PERFD_NETWORK_NETWORK_CONSTANTS_H_

#include <string>
#include <vector>

namespace profiler {

// Utility methods for fetching standard network commands and log files.
class NetworkConstants final {
 public:
  // Path of file that contains all apps' sent and received bytes.
  static const char *const GetTrafficBytesFilePath() {
    return "/proc/net/xt_qtaguid/stats";
  }

  // Path of files that contains all apps' open connection numbers.
  static std::vector<std::string> GetConnectionFilePaths() {
    const char *file_paths[] = {
        "/proc/net/tcp6", "/proc/net/udp6", "/proc/net/raw6",
        "/proc/net/tcp",  "/proc/net/udp",  "/proc/net/raw",
    };
    const int len = sizeof(file_paths) / sizeof(const char *);

    return std::vector<std::string>(file_paths, file_paths + len);
  }

  // Dumpsys command that is relatively efficient to get radio power status.
  static const char *const GetRadioStatusCommand() {
    return "dumpsys network_management";
  }
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_CONSTANTS_H_
