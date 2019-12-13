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
#include "activity_manager.h"

#include <sys/stat.h>
#include <iostream>
#include <sstream>
#include <thread>

#include "utils/clock.h"
#include "utils/current_process.h"
#include "utils/device_info.h"
#include "utils/filesystem_notifier.h"
#include "utils/log.h"
#include "utils/process_manager.h"
#include "utils/trace.h"

using profiler::proto::TraceStopStatus;
using std::string;

namespace {
const char *const kAmExecutable = "/system/bin/am";
}  // namespace

namespace profiler {

ActivityManager::ActivityManager()
    : bash_(new BashCommandRunner(kAmExecutable, true /* log command */)) {}

bool ActivityManager::StartProfiling(const ProfilingMode profiling_mode,
                                     const string &app_package_name,
                                     int sampling_interval_us,
                                     const string &trace_path,
                                     string *error_string,
                                     bool is_startup_profiling) {
  Trace trace("CPU:StartProfiling ART");
  std::lock_guard<std::mutex> lock(profiled_lock_);

  if (IsAppProfiled(app_package_name)) {
    *error_string = "App is already being profiled with ART";
    return false;
  }
  // if |is_startup_profiling| is true, it means that profiling started with
  // activity launch command, so there is no need to start profiling.
  if (!is_startup_profiling) {
    // Run command via actual am.
    std::ostringstream parameters;
    parameters << "profile start ";
    if (profiling_mode == ActivityManager::SAMPLING) {
      // A sample interval in microseconds is required after '--sampling'.
      // Note that '--sampling 0' would direct ART into instrumentation mode.
      // If there's no '--sampling X', instrumentation is used.
      parameters << "--sampling " << sampling_interval_us << " ";
    }
    if (DeviceInfo::feature_level() >= DeviceInfo::O) {
      // Use streaming output mode on O or greater.
      parameters << "--streaming ";
    }
    parameters << ProcessManager::GetCanonicalName(app_package_name);
    parameters << " " << trace_path;
    if (!bash_->Run(parameters.str(), error_string)) {
      *error_string = "Unable to run profile start command";
      return false;
    }
  }
  AddProfiledApp(app_package_name, trace_path);
  return true;
}

TraceStopStatus::Status ActivityManager::StopProfiling(
    const string &app_package_name, bool need_result, string *error_string,
    int32_t timeout_sec, bool is_startup_profiling) {
  Trace trace("CPU:StopProfiling ART");
  std::lock_guard<std::mutex> lock(profiled_lock_);

  // Start monitoring trace events (to catch close) so this method only returns
  // when the generation of the trace file is finished.
  const std::string &trace_path = GetProfiledAppTracePath(app_package_name);
  FileSystemNotifier notifier(trace_path, FileSystemNotifier::CLOSE);

  RemoveProfiledApp(app_package_name);

  if (need_result) {
    if (!notifier.IsReadyToNotify()) {
      *error_string = "Unable to monitor trace file for completion";
      return TraceStopStatus::CANNOT_START_WAITING;
    }
  }

  // Run stop command via actual am.
  if (!RunProfileStopCmd(app_package_name, error_string)) {
    *error_string = "Unable to run profile stop command";
    return TraceStopStatus::STOP_COMMAND_FAILED;
  }

  if (need_result) {
    const int64_t timeout_ms = Clock::s_to_ms(timeout_sec);
    // Because of an issue in the android pre-P platform, it is unreliable to
    // monitor the file close event for a trace which started by "am start
    // --start-profiler" (http://b/73891014). So working around the issue by
    // just waiting for 5 Seconds.
    if (is_startup_profiling && DeviceInfo::feature_level() < DeviceInfo::P) {
      std::this_thread::sleep_for(std::chrono::milliseconds(timeout_ms));
      return TraceStopStatus::SUCCESS;
    }

    // Wait until ART has finished writing the trace to the file and closed the
    // file.
    auto wait_result = notifier.WaitUntilEventOccurs(timeout_ms);
    switch (wait_result) {
      case FileSystemNotifier::kSuccess:
        return TraceStopStatus::SUCCESS;
      case FileSystemNotifier::kTimeout:
        *error_string = "Wait for ART trace file timed out.";
        return TraceStopStatus::WAIT_TIMEOUT;
      case FileSystemNotifier::kCannotReadEvent:
        *error_string = "Cannot read events while waiting for ART trace file.";
        return TraceStopStatus::CANNOT_READ_WAIT_EVENT;
      case FileSystemNotifier::kUnspecified:
        *error_string = "Wait for ART trace file failed.";
        return TraceStopStatus::WAIT_FAILED;
    }
  }

  return TraceStopStatus::SUCCESS;
}

bool ActivityManager::TriggerHeapDump(int pid, const std::string &file_path,
                                      std::string *error_string) const {
  std::stringstream ss;
  ss << "dumpheap " << pid << " " << file_path;
  return bash_->Run(ss.str(), error_string);
}

void ActivityManager::Shutdown() {
  // Intentionally not protected by |profiled_lock_| so this function can
  // proceed without being blocked.
  string error;
  for (auto const &record : profiled_) {
    RunProfileStopCmd(record.first, &error);
  }
}

ActivityManager *ActivityManager::Instance() {
  static ActivityManager *instance = new ActivityManager();
  return instance;
}

bool ActivityManager::IsAppProfiled(const std::string &app_package_name) const {
  return profiled_.find(app_package_name) != profiled_.end();
}

void ActivityManager::AddProfiledApp(const std::string &app_package_name,
                                     const std::string &trace_path) {
  ArtOnGoingProfiling profilingEntry;
  profilingEntry.trace_path = trace_path;
  profilingEntry.app_pkg_name = app_package_name;
  profiled_[app_package_name] = profilingEntry;
}

void ActivityManager::RemoveProfiledApp(const std::string &app_package_name) {
  profiled_.erase(app_package_name);
}

string ActivityManager::GetProfiledAppTracePath(
    const std::string &app_package_name) const {
  auto it = profiled_.find(app_package_name);
  return it->second.trace_path;
}

bool ActivityManager::RunProfileStopCmd(const string &app_package_name,
                                        string *error_string) {
  string parameters;
  parameters.append("profile stop ");
  parameters.append(ProcessManager::GetCanonicalName(app_package_name));
  return bash_->Run(parameters, error_string);
}

}  // namespace profiler
