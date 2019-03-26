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
#include "nonblocking_command_runner.h"

#include <string.h>

#include <utils/log.h>

using profiler::Log;
using std::string;

namespace profiler {

// Integration tests use this file to avoid forking a non-existent command.
// Unit tests DON'T use this but the device variant instead.
bool NonBlockingCommandRunner::Run(const char* const arguments[],
                                   const string& input,
                                   StdoutCallback* callback,
                                   const char* const env_args[]) {
  // This version of the file only runs on host and doesn't actually fork a
  // command.
  if (log_command_) {
    Log::D("Mock command forking: %s", executable_path_.c_str());
  }
  return true;
}

void NonBlockingCommandRunner::Kill() {
  if (IsRunning()) {
    child_process_id_ = 0;
    if (read_data_thread_.joinable()) {
      read_data_thread_.join();
    }
  }
}

}  // namespace profiler
