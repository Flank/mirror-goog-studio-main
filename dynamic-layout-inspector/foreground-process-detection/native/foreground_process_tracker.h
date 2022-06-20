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

/**
 * This file registers two command handlers with the transport daemon.
 * START_TRACKING_FOREGROUND_PROCESS and STOP_TRACKING_FOREGROUND_PROCESS.
 *
 * When START_TRACKING_FOREGROUND_PROCESS is received, a new thread is created.
 * This thread is used to periodically execute a dumpsys command.
 *
 * The dumpsys command output contains information about the foreground Activity
 * running on the phone. This output is parsed using a regexp, from which
 * process PID and process name are extracted.
 *
 * If PID and process name have changed since the last time dumpsys was called,
 * an event is sent to Studio, to notify the change of foreground process.
 *
 * The polling continues either until STOP_TRACKING_FOREGROUND_PROCESS is
 * received, or until the transport daemon is terminated. The transport daemon
 * is a subprocess of adb shell. Therefore it will terminate if the device is
 * unplugged or Studio terminates.
 */

#ifndef DYNAMIC_LAYOUT_INSPECTOR_FOREGROUND_PROCESS_DETECTION_FOREGROUND_PROCESS_TRACKER_
#define DYNAMIC_LAYOUT_INSPECTOR_FOREGROUND_PROCESS_DETECTION_FOREGROUND_PROCESS_TRACKER_

#include <unistd.h>
#include <regex>
#include <string>

#include "commands/is_tracking_foreground_process_supported.h"
#include "commands/start_tracking_foreground_process.h"
#include "commands/stop_tracking_foreground_process.h"
#include "daemon/event_buffer.h"
#include "utils/bash_command.h"

namespace layout_inspector {

struct ProcessInfo {
  bool isEmpty = true;
  std::string pid;
  std::string processName;
};

class ForegroundProcessTracker {
 public:
  static ForegroundProcessTracker& Instance(profiler::EventBuffer* buffer) {
    static auto instance = ForegroundProcessTracker(buffer);
    return instance;
  }

  static void Initialize(profiler::Daemon* daemon) {
    auto daemon_config = daemon->config()->GetConfig();

    bool is_autoconnect_enabled =
        daemon_config.layout_inspector_config().autoconnect_enabled();
    if (!is_autoconnect_enabled) {
      return;
    }

    daemon->RegisterCommandHandler(
        profiler::proto::Command::IS_TRACKING_FOREGROUND_PROCESS_SUPPORTED,
        &IsTrackingForegroundProcessSupported::Create);
    daemon->RegisterCommandHandler(
        profiler::proto::Command::START_TRACKING_FOREGROUND_PROCESS,
        &StartTrackingForegroundProcess::Create);
    daemon->RegisterCommandHandler(
        profiler::proto::Command::STOP_TRACKING_FOREGROUND_PROCESS,
        &StopTrackingForegroundProcess::Create);
  }

  // Public for testing
  static constexpr int kPollingDelayMs = 250;

  // Main constructor takes BashCommandRunner to facilitate mocking it in unit
  // tests
  ForegroundProcessTracker(
      profiler::EventBuffer* buffer,
      profiler::BashCommandRunner* dumpsysTopActivityCommandRunner,
      profiler::BashCommandRunner* dumpsysSleepingActivitiesCommandRunner,
      profiler::BashCommandRunner* dumpsysAwakeActivitiesCommandRunner)
      : dumpsysTopActivityCommandRunner_(dumpsysTopActivityCommandRunner),
        dumpsysSleepingActivitiesCommandRunner_(
            dumpsysSleepingActivitiesCommandRunner),
        dumpsysAwakeActivitiesCommandRunner_(
            dumpsysAwakeActivitiesCommandRunner),
        shouldDoPolling_(false),
        isThreadRunning_(false) {
    eventBuffer_ = buffer;
  }

  ~ForegroundProcessTracker() {
    shouldDoPolling_.store(false);
    if (isThreadRunning_.load()) {
      if (workerThread_.joinable()) {
        workerThread_.join();
        isThreadRunning_.store(false);
      }
    }
    delete dumpsysTopActivityCommandRunner_;
    delete dumpsysSleepingActivitiesCommandRunner_;
    delete dumpsysAwakeActivitiesCommandRunner_;
  }

  // Runs dumpsys and tries to extract the foreground process for its output.
  // Returns false if foreground process info can't be extracted.
  TrackingForegroundProcessSupported::SupportType
  IsTrackingForegroundProcessSupported();
  void StartTracking();
  void StopTracking();

 private:
  // Constructor used for non-test scenarios. Uses BashCommandRunner to call
  // dumpsys
  ForegroundProcessTracker(profiler::EventBuffer* buffer)
      : ForegroundProcessTracker(
            buffer,
            new profiler::BashCommandRunner{
                "dumpsys activity processes | grep top-activity", false},
            new profiler::BashCommandRunner{
                "dumpsys activity activities | grep isSleeping=true", false},
            new profiler::BashCommandRunner{
                "dumpsys activity activities | grep isSleeping=false", false}) {
  }

  ProcessInfo runDumpsysTopActivityCommand();
  bool hasSleepingActivities();
  bool hasAwakeActivities();

  // Sends foregrond process data to Studio
  void sendForegroundProcessEvent(const ProcessInfo& processInfo);

  void doPolling();

  // Extracts PID and process name from the dumpsys output passed as input
  ProcessInfo parseProcessInfo(const std::string& dumpsysOutput);

  // EventBuffer from transport
  profiler::EventBuffer* eventBuffer_;

  profiler::BashCommandRunner* dumpsysTopActivityCommandRunner_;
  profiler::BashCommandRunner* dumpsysSleepingActivitiesCommandRunner_;
  profiler::BashCommandRunner* dumpsysAwakeActivitiesCommandRunner_;

  // Used to keep track of the last seen foreground process
  ProcessInfo latestForegroundProcess_;

  // Thread used to do the polling
  std::thread workerThread_;
  std::atomic_bool shouldDoPolling_;
  std::atomic_bool isThreadRunning_;
};

}  // namespace layout_inspector

#endif  // DYNAMIC_LAYOUT_INSPECTOR_FOREGROUND_PROCESS_DETECTION_FOREGROUND_PROCESS_TRACKER_