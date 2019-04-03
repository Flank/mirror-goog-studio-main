/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "daemon_config.h"

#include <fstream>
#include "utils/device_info.h"
#include "utils/log.h"

namespace profiler {

DaemonConfig::DaemonConfig(const proto::DaemonConfig& daemon_config)
    : daemon_config_(daemon_config) {}

DaemonConfig::DaemonConfig(const std::string& file_path) {
  std::fstream input(file_path, std::ios::in | std::ios::binary);
  if (!daemon_config_.ParseFromIstream(&input)) {
    Log::V("Failed to parse config from %s", file_path.c_str());
  }
  input.close();
}

}  // namespace profiler
