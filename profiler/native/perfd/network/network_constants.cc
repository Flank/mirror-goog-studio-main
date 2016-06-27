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
#include "network_constants.h"

namespace {
const char* const kTrafficBytesFilePath = "/proc/net/xt_qtaguid/stats";
const char* const kConnectionFilePaths[] = {"/proc/net/tcp6", "/proc/net/udp6",
                                            "/proc/net/raw6", "/proc/net/tcp",
                                            "/proc/net/udp",  "/proc/net/raw"};
const char* const kRadioStatusCommand = "dumpsys network_management";
const char* const kDefaultNetworkTypeCommand = "dumpsys connectivity";
}  // anonymous namespace

namespace profiler {

std::string NetworkConstants::GetPidStatusFilePath(const int pid) {
  char buffer[64];
  snprintf(buffer, sizeof(buffer), "/proc/%d/status", pid);
  return buffer;
}

const char* const NetworkConstants::GetTrafficBytesFilePath() {
  return kTrafficBytesFilePath;
}

std::vector<const char*> NetworkConstants::GetConnectionFilePaths() {
  std::vector<const char*> files;
  for (const char* const file : kConnectionFilePaths) {
    files.push_back(file);
  }
  return files;
}

const char* const NetworkConstants::GetRadioStatusCommand() {
  return kRadioStatusCommand;
}

const char* const NetworkConstants::GetDefaultNetworkTypeCommand() {
  return kDefaultNetworkTypeCommand;
}

}  // namespace profiler
