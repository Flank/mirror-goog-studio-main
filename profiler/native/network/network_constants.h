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
#ifndef NETWORK_NETWORK_FILES_H_
#define NETWORK_NETWORK_FILES_H_

#include <string>
#include <vector>

namespace profiler {

// Utility methods for fetching standard network log files
// TODO: Make this class instantiatable and mockable
class NetworkConstants final {
 public:
  // Path of pid status file to get uid from pid.
  static std::string GetPidStatusFilePath(const int pid) {
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "/proc/%d/status", pid);
    return buffer;
  }

  // Path of file that contains all apps' sent and received bytes.
  static const std::string &GetTrafficBytesFilePath() {
    static const std::string file_path("/proc/net/xt_qtaguid/stats");
    return file_path;
  }

  // Path of files that contains all apps' open connection numbers.
  static const std::vector<std::string> &GetConnectionFilePaths() {
    static const std::vector<std::string> file_paths{{
        "/proc/net/tcp6", "/proc/net/udp6", "/proc/net/raw6", "/proc/net/tcp",
        "/proc/net/udp", "/proc/net/raw",
    }};
    return file_paths;
  }

  // Dumpsys command that is relatively efficient to get radio power status.
  static const std::string &GetRadioStatusCommand() {
    static const std::string radio("dumpsys network_management");
    return radio;
  }

  // Dumpsys command that is relatively efficient to get default network type.
  static const std::string &GetDefaultNetworkTypeCommand() {
    static const std::string default_type("dumpsys connectivity");
    return default_type;
  }
};

}  // namespace profiler

#endif  // NETWORK_NETWORK_FILES_H_
