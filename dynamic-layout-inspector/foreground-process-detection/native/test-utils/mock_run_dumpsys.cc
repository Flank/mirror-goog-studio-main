/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "foreground_process_tracker.h"

#include <string>

namespace layout_inspector {

namespace {
int mock_bash_command_runner_count = 0;
}  // anonymous namespace

// This file is used to mock |runDumpsysCommand| for integration tests with
// fakeandroid. Since fakeandroid doesn't have dumpdsys we can't use the real
// implementation.
ProcessInfo ForegroundProcessTracker::runDumpsysCommand() {
  ProcessInfo processInfo{};

  if (mock_bash_command_runner_count % 2 == 0) {
    processInfo.pid = "1";
    processInfo.processName = "fake.process1";
    processInfo.isEmpty = false;
  } else {
    processInfo.pid = "2";
    processInfo.processName = "fake.process2";
    processInfo.isEmpty = false;
  }

  mock_bash_command_runner_count += 1;

  return processInfo;
}

}  // namespace layout_inspector