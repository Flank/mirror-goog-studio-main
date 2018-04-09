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
#include "perfd/cpu/cpu_cache.h"

#include <algorithm>
#include <cstdint>
#include <iterator>
#include <mutex>
#include <vector>

#include "utils/process_manager.h"

using profiler::proto::CpuUsageData;
using std::string;
using std::vector;

namespace profiler {

bool CpuCache::AllocateAppCache(int32_t pid) {
  if (FindAppCache(pid) != nullptr) return true;
  app_caches_.emplace_back(new AppCpuCache(pid, capacity_));
  return true;
}

bool CpuCache::DeallocateAppCache(int32_t pid) {
  for (auto it = app_caches_.begin(); it != app_caches_.end(); it++) {
    if (pid == (*it)->pid) {
      app_caches_.erase(it);
      return true;
    }
  }
  return false;
}

bool CpuCache::Add(int32_t pid, const CpuUsageData& datum) {
  auto* found = FindAppCache(pid);
  if (found == nullptr) return false;
  found->usage_cache.Add(datum, datum.end_timestamp());
  return true;
}

vector<CpuUsageData> CpuCache::Retrieve(int32_t pid, int64_t from, int64_t to) {
  auto* found = FindAppCache(pid);
  if (found == nullptr) {
    vector<CpuUsageData> empty;
    return empty;
  }
  return found->usage_cache.GetValues(from, to);
}

bool CpuCache::AddThreads(int32_t pid, const ThreadsSample& sample) {
  auto* found = FindAppCache(pid);
  if (found == nullptr) return false;
  found->threads_cache.Add(sample, sample.snapshot.timestamp());
  return true;
}

CpuCache::ThreadSampleResponse CpuCache::GetThreads(int32_t pid, int64_t from,
                                                    int64_t to) {
  CpuCache::ThreadSampleResponse response;
  const ThreadsSample* latest_before_from = nullptr;
  auto* found = FindAppCache(pid);
  if (found == nullptr) {
    return response;
  }
  auto threads_cache_content = found->threads_cache.GetValues(INT64_MIN, to);

  // TODO: optimize it to binary search the initial point. That will also make
  // it easier to get the data from the greatest timestamp smaller than |from|.
  for (const auto& sample : threads_cache_content) {
    auto timestamp = sample.snapshot.timestamp();
    if (timestamp > from && timestamp <= to) {
      response.activity_samples.push_back(sample);
    }

    // Update the latest sample that was registered before (or at the
    // same time) of the request start timestamp, in case there is one.
    if (timestamp <= from &&
        (latest_before_from == nullptr ||
         timestamp > latest_before_from->snapshot.timestamp())) {
      latest_before_from = &sample;
    }
  }

  if (latest_before_from != nullptr) {
    // Add the snapshot to the response
    response.snapshot = latest_before_from->snapshot;
  }

  return response;
}

int32_t CpuCache::AddProfilingStart(int32_t pid, const ProfilingApp& record) {
  auto* found = FindAppCache(pid);
  if (found == nullptr) return -1;
  found->ongoing_capture = found->capture_cache.Add(record);
  int32_t id = GenerateTraceId();
  found->ongoing_capture->trace_id = id;
  return id;
}

bool CpuCache::AddProfilingStop(int32_t pid) {
  auto* found = FindAppCache(pid);
  if (found == nullptr) return false;
  if (found->ongoing_capture == nullptr) return false;
  found->ongoing_capture->end_timestamp = clock_->GetCurrentTime();
  found->ongoing_capture = nullptr;
  return true;
}

int32_t CpuCache::AddStartupProfilingStart(const string& apk_pkg_name,
                                           const ProfilingApp& record) {
  startup_profiling_apps_[apk_pkg_name] = record;
  int32_t id = GenerateTraceId();
  startup_profiling_apps_[apk_pkg_name].trace_id = id;
  return id;
}

void CpuCache::AddStartupProfilingStop(const string& apk_pkg_name) {
  startup_profiling_apps_.erase(apk_pkg_name);
}

ProfilingApp* CpuCache::GetOngoingCapture(int32_t pid) {
  // First, look into pid-associated |app_caches_|.
  auto* found = FindAppCache(pid);
  if (found == nullptr) return nullptr;
  if (found->ongoing_capture != nullptr) return found->ongoing_capture;

  // If there is no apps under startup profiling, there is no point in trying to
  // find a package name corresponding to |pid|, so we return early to prevent a
  // call to |ProcessManager::GetCmdlineForPid|, which can be quite expensive.
  if (startup_profiling_apps_.empty()) {
    return nullptr;
  }

  // Not in |app_caches_|, try to find in |startup_profiling_apps_|.
  string app_pkg_name = ProcessManager::GetCmdlineForPid(pid);
  return GetOngoingStartupProfiling(app_pkg_name);
}

ProfilingApp* CpuCache::GetOngoingStartupProfiling(
    const std::string& app_pkg_name) {
  const auto& app_iterator = startup_profiling_apps_.find(app_pkg_name);
  if (app_iterator != startup_profiling_apps_.end()) {
    return &app_iterator->second;
  }
  return nullptr;
}

vector<ProfilingApp> CpuCache::GetCaptures(int32_t pid, int64_t from,
                                           int64_t to) {
  auto* found = FindAppCache(pid);
  if (found == nullptr) {
    vector<ProfilingApp> empty;
    return empty;
  }
  auto& cache = found->capture_cache;
  vector<ProfilingApp> captures;
  for (size_t i = 0; i < cache.size(); i++) {
    const auto& candidate = cache.Get(i);
    // Skip completed captures that ends earlier than |from| and those
    // (completed or not) that starts after |to|.
    if ((candidate.end_timestamp != -1 && candidate.end_timestamp < from) ||
        candidate.start_timestamp > to)
      continue;
    captures.push_back(candidate);
  }
  return captures;
}

bool CpuCache::AddTraceContent(int32_t pid, int32_t trace_id,
                               const std::string& trace_content) {
  const string& file_name = GetCachedFileName(pid, trace_id);
  file_cache_->AddChunk(file_name, trace_content);
  file_cache_->Complete(file_name);
  return true;
}

bool CpuCache::RetrieveTraceContent(int32_t pid, int32_t trace_id,
                                    string* output) {
  std::shared_ptr<File> file =
      file_cache_->GetFile(GetCachedFileName(pid, trace_id));
  if (file.get() != nullptr) {
    *output = file->Contents();
    return true;
  }
  return false;
}

CpuCache::AppCpuCache* CpuCache::FindAppCache(int32_t pid) {
  for (auto& cache : app_caches_) {
    if (pid == cache->pid) {
      return cache.get();
    }
  }
  return nullptr;
}

int32_t CpuCache::GenerateTraceId() {
  static int32_t trace_id = 0;
  return trace_id++;
}

string CpuCache::GetCachedFileName(int32_t pid, int32_t trace_id) {
  std::ostringstream oss;
  oss << "CpuTraceContent-" << pid << "-" << trace_id << ".trace";
  return oss.str();
}

}  // namespace profiler
