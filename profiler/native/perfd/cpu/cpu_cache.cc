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

void CpuCache::AddProfilingStart(int32_t pid, const ProfilingApp& record) {
  profiling_apps_[pid] = record;
}

void CpuCache::AddProfilingStop(int32_t pid) { profiling_apps_.erase(pid); }

void CpuCache::AddStartupProfilingStart(const string& apk_pkg_name,
                                        const ProfilingApp& record) {
  startup_profiling_apps_[apk_pkg_name] = record;
}

void CpuCache::AddStartupProfilingStop(const string& apk_pkg_name) {
  startup_profiling_apps_.erase(apk_pkg_name);
}

// Looks from |profiling_apps_|, if not found then from
// |startup_profiling_apps_|, otherwise returns null.
ProfilingApp* CpuCache::GetProfilingApp(int32_t pid) {
  const auto& app_iterator = profiling_apps_.find(pid);
  if (app_iterator != profiling_apps_.end()) {
    return &app_iterator->second;
  }
  // Try to find in |startup_profiling_apps_|
  string app_pkg_name = ProcessManager::GetCmdlineForPid(pid);
  if (app_pkg_name.empty()) {
    return nullptr;
  }
  const auto& startup_app_iterator = startup_profiling_apps_.find(app_pkg_name);
  if (startup_app_iterator != startup_profiling_apps_.end()) {
    return &startup_app_iterator->second;
  }

  return nullptr;
}

CpuCache::AppCpuCache* CpuCache::FindAppCache(int32_t pid) {
  for (auto& cache : app_caches_) {
    if (pid == cache->pid) {
      return cache.get();
    }
  }
  return nullptr;
}

}  // namespace profiler
