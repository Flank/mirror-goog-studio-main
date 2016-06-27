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
// The file paths may be constants or may be dynamically computed.
class NetworkConstantsInterface {
 public:
  virtual ~NetworkConstantsInterface() = default;
  // Path of pid status file to get uid from pid.
  virtual std::string GetPidStatusFilePath(const int pid) = 0;
  // Path of file that contains all apps' sent and received bytes.
  virtual const char* const GetTrafficBytesFilePath() = 0;
  // Path of files that contains all apps' open connection numbers.
  virtual std::vector<const char*> GetConnectionFilePaths() = 0;
  // Dumpsys command that is relatively efficient to get radio power status.
  virtual const char* const GetRadioStatusCommand() = 0;
  // Dumpsys command that is relatively efficient to get default network type.
  virtual const char* const GetDefaultNetworkTypeCommand() = 0;
};

class NetworkConstants final : public NetworkConstantsInterface {
 public:
  std::string GetPidStatusFilePath(const int pid) override;
  const char* const GetTrafficBytesFilePath() override;
  std::vector<const char*> GetConnectionFilePaths() override;
  const char* const GetRadioStatusCommand() override;
  const char* const GetDefaultNetworkTypeCommand() override;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_CONSTANTS_H_
