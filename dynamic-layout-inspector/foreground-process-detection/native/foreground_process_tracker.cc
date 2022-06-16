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

#include <unistd.h>
#include <regex>
#include <string>

namespace layout_inspector {

bool ForegroundProcessTracker::IsTrackingForegroundProcessSupported() {
  ProcessInfo processInfo = runDumpsysCommand();
  return !processInfo.isEmpty;
}

void ForegroundProcessTracker::StartTracking() {
  if (shouldDoPolling_.load() || isThreadRunning_.load()) {
    return;
  }

  shouldDoPolling_.store(true);
  isThreadRunning_.store(true);

  // Start a new thread were we can do the polling
  workerThread_ = std::thread([this]() {
    while (shouldDoPolling_.load()) {
      doPolling();
      std::this_thread::sleep_for(std::chrono::milliseconds(kPollingDelayMs));
    }
  });
}

void ForegroundProcessTracker::StopTracking() {
  shouldDoPolling_.store(false);
  workerThread_.join();
  isThreadRunning_.store(false);
  latestForegroundProcess_ = {};
}

void ForegroundProcessTracker::sendForegroundProcessEvent(
    const ProcessInfo& processInfo) {
  profiler::proto::Event event;
  event.set_kind(profiler::proto::Event::LAYOUT_INSPECTOR_FOREGROUND_PROCESS);
  auto* layoutInspectorForegroundProcess =
      event.mutable_layout_inspector_foreground_process();
  layoutInspectorForegroundProcess->set_process_name(
      processInfo.processName.c_str());
  layoutInspectorForegroundProcess->set_pid(processInfo.pid.c_str());

  eventBuffer_->Add(event);
}

void ForegroundProcessTracker::doPolling() {
  ProcessInfo processInfo = runDumpsysCommand();

  if (!processInfo.isEmpty &&
      latestForegroundProcess_.pid.compare(processInfo.pid) != 0) {
    // Foreground process has changed, send event to Studio
    latestForegroundProcess_ = processInfo;
    sendForegroundProcessEvent(processInfo);
  }
}

ProcessInfo ForegroundProcessTracker::parseProcessInfo(
    const std::string& dumpsysOutput) {
  ProcessInfo processInfo{};
  std::smatch matches;

  // Regexp used to extract PID:PROCESS_NAME from the output of dumpsys
  // TODO use tracer to measure the performance difference between grep and
  // regexp
  std::regex regexp("(\\d*):(\\S*)\\/\\S* \\(top-activity\\)");
  regex_search(dumpsysOutput, matches, regexp);

  if (matches.size() < 3) {
    // Regex has no matches
    return processInfo;
  }

  std::string pid = matches.str(1);
  std::string processName = matches.str(2);

  processInfo.pid = pid;
  processInfo.processName = processName;
  processInfo.isEmpty = false;

  return processInfo;
}

}  // namespace layout_inspector