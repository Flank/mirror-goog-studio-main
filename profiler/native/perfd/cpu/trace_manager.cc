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
#include "perfd/cpu/trace_manager.h"

namespace profiler {

static const int64_t kTraceRecordBufferSize = 10;

bool TraceManager::UsePerfetto() {
  return DeviceInfo::feature_level() >= DeviceInfo::P &&
         cpu_config_.use_perfetto();
}

ProfilingApp* TraceManager::StartProfiling(
    int64_t request_timestamp_ns,
    const proto::CpuTraceConfiguration& configuration, std::string* error) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  const auto& app_name = configuration.app_name();
  // obtain the CircularBuffer, create in place if one does not exist already.
  CircularBuffer<ProfilingApp>& cache =
      capture_cache_
          .emplace(std::piecewise_construct, std::forward_as_tuple(app_name),
                   std::forward_as_tuple(kTraceRecordBufferSize))
          .first->second;
  // Early-out if there is an ongoing previous capture.
  if (!cache.empty() && cache.back().end_timestamp == -1) {
    *error = "ongoing capture already exists";
    return nullptr;
  }

  bool success = false;
  if (configuration.initiation_type() == proto::INITIATED_BY_API) {
    // Special case for API-initiated tracing: Only cache the ProfilingApp
    // record, as the trace logic is handled via the app.
    success = true;
  } else {
    const auto& user_options = configuration.user_options();
    bool startup_profiling =
        configuration.initiation_type() == proto::INITIATED_BY_STARTUP;
    if (user_options.trace_type() == proto::CpuTraceType::SIMPLEPERF) {
      success = simpleperf_manager_->StartProfiling(
          app_name, configuration.abi_cpu_arch(),
          user_options.sampling_interval_us(), configuration.temp_path(), error,
          startup_profiling);
    } else if (user_options.trace_type() == proto::CpuTraceType::ATRACE) {
      // TODO report back the acquired buffer size. Currently it is not used.
      int acquired_buffer_size_kb = 0;
      if (UsePerfetto()) {
        // Perfetto always acquires the proper buffer size.
        acquired_buffer_size_kb = user_options.buffer_size_in_mb() * 1024;
        // TODO: We may want to pass this in from studio for a more flexible
        // config.
        perfetto::protos::TraceConfig config =
            PerfettoManager::BuildConfig(app_name, acquired_buffer_size_kb);
        success = perfetto_manager_->StartProfiling(
            app_name, configuration.abi_cpu_arch(), config,
            configuration.temp_path(), error);
      } else {
        success = atrace_manager_->StartProfiling(
            app_name, user_options.buffer_size_in_mb(),
            &acquired_buffer_size_kb, configuration.temp_path(), error);
      }
    } else {
      auto mode = user_options.trace_mode() == proto::CpuTraceMode::INSTRUMENTED
                      ? ActivityManager::INSTRUMENTED
                      : ActivityManager::SAMPLING;
      success = activity_manager_->StartProfiling(
          mode, app_name, user_options.sampling_interval_us(),
          configuration.temp_path(), error, startup_profiling);
    }
  }

  if (success) {
    ProfilingApp profiling_app;
    profiling_app.trace_id = clock_->GetCurrentTime();
    profiling_app.start_timestamp = request_timestamp_ns;
    profiling_app.end_timestamp = -1;  // -1 means trace is ongoing
    profiling_app.configuration = configuration;

    return cache.Add(profiling_app);
  } else {
    return nullptr;
  }
}

ProfilingApp* TraceManager::StopProfiling(
    int64_t request_timestamp_ns, const std::string& app_name, bool need_trace,
    proto::TraceStopStatus::Status* status, std::string* error) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  auto* ongoing_capture = GetOngoingCapture(app_name);
  if (ongoing_capture == nullptr) {
    *error = "No ongoing capture exists";
    *status = proto::TraceStopStatus::NO_ONGOING_PROFILING;
    return nullptr;
  }

  *status = proto::TraceStopStatus::SUCCESS;

  if (ongoing_capture->configuration.initiation_type() ==
      proto::INITIATED_BY_API) {
    // Special for API-initiated tracing: only update the
    // ProfilingApp record, as the trace logic is handled via the app.
    // End timestamp should come from when the stop request was invoked
    // in the app.
    ongoing_capture->end_timestamp = request_timestamp_ns;
  } else {
    auto trace_type =
        ongoing_capture->configuration.user_options().trace_type();
    if (trace_type == proto::CpuTraceType::SIMPLEPERF) {
      *status = simpleperf_manager_->StopProfiling(
          app_name, need_trace, cpu_config_.simpleperf_host(), error);
    } else if (trace_type == proto::CpuTraceType::ATRACE) {
      if (UsePerfetto()) {
        *status = perfetto_manager_->StopProfiling(error);
      } else {
        *status = atrace_manager_->StopProfiling(app_name, need_trace, error);
      }
    } else {  // Profiler is ART
      *status = activity_manager_->StopProfiling(
          app_name, need_trace, error, cpu_config_.art_stop_timeout_sec(),
          ongoing_capture->configuration.initiation_type() ==
              proto::INITIATED_BY_STARTUP);
    }
    ongoing_capture->end_timestamp = clock_->GetCurrentTime();
  }

  return ongoing_capture;
}

ProfilingApp* TraceManager::GetOngoingCapture(const std::string& app_name) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  auto itr = capture_cache_.find(app_name);
  if (itr == capture_cache_.end()) {
    return nullptr;
  }

  CircularBuffer<ProfilingApp>& cache = itr->second;
  if (!cache.empty() && cache.back().end_timestamp == -1) {
    return &cache.back();
  }

  return nullptr;
}

std::vector<ProfilingApp> TraceManager::GetCaptures(const std::string& app_name,
                                                    int64_t from, int64_t to) {
  std::lock_guard<std::recursive_mutex> lock(capture_mutex_);

  std::vector<ProfilingApp> captures;
  auto itr = capture_cache_.find(app_name);
  if (itr == capture_cache_.end()) {
    return captures;
  }

  CircularBuffer<ProfilingApp>& cache = itr->second;
  for (size_t i = 0; i < cache.size(); i++) {
    const auto& candidate = cache.Get(i);
    // Skip completed captures that ends earlier than |from| and those
    // (completed or not) that starts after |to|.
    if ((candidate.end_timestamp != -1 && candidate.end_timestamp < from) ||
        candidate.start_timestamp > to) {
      continue;
    }
    captures.push_back(candidate);
  }

  return captures;
}

}  // namespace profiler
