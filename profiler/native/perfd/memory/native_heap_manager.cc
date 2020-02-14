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
#include "native_heap_manager.h"

#include <fstream>
#include <sstream>
#include <string>
#include "utils/clock.h"
#include "utils/device_info.h"
#include "utils/filesystem_notifier.h"
#include "utils/thread_name.h"

using ::profiler::proto::StartNativeSample;
using std::string;

namespace profiler {

NativeHeapManager::~NativeHeapManager() {}

bool NativeHeapManager::StartSample(int64_t id, const StartNativeSample& config,
                                    string* error_message) {
  if (is_ongoing_capture_) {
    return true;
  }
  perfetto::protos::TraceConfig traceConfig =
      PerfettoManager::BuildHeapprofdConfig(
          config.app_name(), config.sampling_interval_bytes(),
          config.continuous_dump_interval_ms(),
          config.shared_memory_buffer_bytes());
  bool success = perfetto_manager_.StartProfiling(
      config.app_name(), config.abi_cpu_arch(), traceConfig, config.temp_path(),
      error_message);
  ongoing_capture_path_ = config.temp_path();
  is_ongoing_capture_ = success;
  ongoing_capture_id_ = id;
  return success;
}

bool NativeHeapManager::StopSample(int64_t capture_id, string* error_message) {
  if (!is_ongoing_capture_) {
    error_message->append(" No ongoing capture to stop.");
    return false;
  }
  bool success = perfetto_manager_.StopProfiling(error_message);
  is_ongoing_capture_ = false;

  if (capture_id != ongoing_capture_id_) {
    error_message->append(
        " Supplied capture id does not match ongoing capture id.");
    return false;
  }

  if (success) {
    std::ostringstream oss;
    oss << capture_id;
    std::string file_id = oss.str();
    success =
        file_cache_->MoveFileToCompleteCache(file_id, ongoing_capture_path_);
    if (!success) {
      error_message->append(" Failed to copy file to cache.");
    }
  }
  return success;
}

}  // namespace profiler
