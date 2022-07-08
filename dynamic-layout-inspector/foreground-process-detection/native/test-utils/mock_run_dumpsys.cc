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

// This file is used to mock |runDumpsysCommand| for integration tests with
// fakeandroid. Since fakeandroid doesn't have dumpdsys we can't use the real
// implementation.

// Each function has a counter to keep track how many times they have been
// invoked.
// This is relevant in particular for the handshake, where the result
// depends on the outcome of all three functions.
namespace layout_inspector {

namespace {
int get_top_activity_command_runner_count = 0;
int has_sleeping_activity_command_runner_count = 0;
int has_awake_activity_command_runner_count = 0;
}  // anonymous namespace

bool ForegroundProcessTracker::hasDumpsys() { return true; }

bool ForegroundProcessTracker::hasGrep() { return true; }

ProcessInfo ForegroundProcessTracker::runDumpsysTopActivityCommand() {
  ProcessInfo processInfo{};

  if (get_top_activity_command_runner_count == 0) {
    processInfo.pid = "1";
    processInfo.processName = "fake.process1";
    processInfo.isEmpty = false;
  } else if (get_top_activity_command_runner_count == 1) {
    processInfo.pid = "2";
    processInfo.processName = "fake.process2";
    processInfo.isEmpty = false;
  } else {
    processInfo.isEmpty = true;
  }

  get_top_activity_command_runner_count += 1;

  return processInfo;
}

// Runs dumpsys to check if we can detect sleeping Activities
bool ForegroundProcessTracker::hasSleepingActivities() {
  if (has_sleeping_activity_command_runner_count == 0) {
    // SUPPORTED
    return true;
  } else if (has_sleeping_activity_command_runner_count == 1) {
    // SUPPORTED
    return true;
  } else if (has_sleeping_activity_command_runner_count == 2) {
    // NOT_SUPPORTED
    return false;
  } else if (has_sleeping_activity_command_runner_count == 3) {
    // UNKNOWN
    return true;
  }

  has_sleeping_activity_command_runner_count += 1;

  return true;
}

// Runs dumpsys to check if we can detect awake Activities
bool ForegroundProcessTracker::hasAwakeActivities() {
  if (has_awake_activity_command_runner_count == 0) {
    // SUPPORTED
    return true;
  } else if (has_awake_activity_command_runner_count == 1) {
    // SUPPORTED
    return true;
  } else if (has_awake_activity_command_runner_count == 2) {
    // NOT_SUPPORTED
    return false;
  } else if (has_awake_activity_command_runner_count == 3) {
    // UNKNOWN
    return false;
  }

  has_awake_activity_command_runner_count += 1;

  return true;
}

}  // namespace layout_inspector