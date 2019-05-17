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
#include "utils/process_manager.h"
#include "utils/clock.h"

#include <string.h>
#include <unistd.h> // for usleep

using std::string;

namespace profiler {
const int kRetryCount = 20;
const int kSleepMsPerRetry = 100;

bool NonBlockingCommandRunner::Run(const char* const arguments[],
                                   StdoutCallback* callback) {
  return Run(arguments, std::string(), callback, nullptr);
}

bool NonBlockingCommandRunner::Run(const char* const arguments[],
                                   const string& input) {
  return Run(arguments, input, nullptr, nullptr);
}

bool NonBlockingCommandRunner::Run(const char* const arguments[],
                                   const string& input,
                                   const char* const env_args[]) {
  return Run(arguments, input, nullptr, env_args);
}

bool NonBlockingCommandRunner::BlockUntilChildprocessExec() {
  for (int i = 0; i < kRetryCount; i++) {
    std::string contents = ProcessManager::GetCmdlineForPid(child_process_id_);
    // Empty contents are returned if the file doesn't exist.
    if (contents.empty()) {
      return false;
    }
    if (contents.find(executable_path_) == 0) {
      return true;
    }
    usleep(Clock::ms_to_us(kSleepMsPerRetry));
  }
  return false;
}
}  // namespace profiler
