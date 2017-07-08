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

#include "config.h"

#include <fstream>
#include "utils/log.h"

namespace profiler {

Config& Config::Instance() {
  static Config* instance = new Config(kConfigFilePath);
  return *instance;
}

Config::Config(const char* file_path) {
  std::fstream input(file_path, std::ios::in | std::ios::binary);
  if (!agent_config_.ParseFromIstream(&input)) {
    Log::V("Failed to parse config from %s", file_path);
  }
  input.close();
}

void Config::SetClientContextTimeout(grpc::ClientContext* context,
                                     int32_t to_sec, int32_t to_msec) {
  std::chrono::system_clock::time_point deadline =
      std::chrono::system_clock::now() + std::chrono::seconds(to_sec) +
      std::chrono::milliseconds(to_msec);
  context->set_deadline(deadline);
}

}  // namespace profiler
