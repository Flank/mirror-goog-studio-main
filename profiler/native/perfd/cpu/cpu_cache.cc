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

#include "proto/profiler.pb.h"

using profiler::proto::CpuProfilerData;
using std::vector;

namespace profiler {

bool CpuCache::AllocateAppCache(int32_t app_id) {
  if (FindAppCache(app_id) != nullptr) return true;
  app_caches_.emplace_back(new AppCpuCache(app_id, capacity_));
  return true;
}

bool CpuCache::DeallocateAppCache(int32_t app_id) {
  for (auto it = app_caches_.begin(); it != app_caches_.end(); it++) {
    if (app_id == (*it)->app_id) {
      app_caches_.erase(it);
      return true;
    }
  }
  return false;
}

bool CpuCache::Add(const CpuProfilerData& datum) {
  int32_t app_id = datum.basic_info().process_id();
  auto* found = FindAppCache(app_id);
  if (found == nullptr) return false;
  found->usage_cache.Add(datum, datum.basic_info().end_timestamp());
  return true;
}

vector<CpuProfilerData> CpuCache::Retrieve(int32_t app_id, int64_t from,
                                           int64_t to) {
  auto* found = FindAppCache(app_id);
  if (found == nullptr) {
    vector<CpuProfilerData> empty;
    return empty;
  }
  return found->usage_cache.GetValues(from, to);
}

bool CpuCache::AddThreads(const ThreadsSample& sample) {
  int32_t app_id = sample.basic_info.process_id();
  auto* found = FindAppCache(app_id);
  if (found == nullptr) return false;
  found->threads_cache.Add(sample, sample.basic_info.end_timestamp());
  return true;
}

CpuCache::ThreadSampleResponse CpuCache::GetThreads(int32_t app_id,
                                                    int64_t from, int64_t to) {
  CpuCache::ThreadSampleResponse response;
  const ThreadsSample* latest_before_from = nullptr;
  auto* found = FindAppCache(app_id);
  if (found == nullptr) {
    return response;
  }
  auto threads_cache_content = found->threads_cache.GetValues(INT64_MIN, to);

  // TODO: optimize it to binary search the initial point. That will also make
  // it easier to get the data from the greatest timestamp smaller than |from|.
  for (const auto& sample : threads_cache_content) {
    auto id = sample.basic_info.process_id();
    auto timestamp = sample.basic_info.end_timestamp();
    if (id == app_id || app_id == proto::AppId::ANY) {
      if (timestamp > from && timestamp <= to) {
        response.activity_samples.push_back(sample);
      }
    }
    // Update the latest sample that was registered before (or at the
    // same time) of the request start timestamp, in case there is one.
    if (timestamp <= from &&
        (latest_before_from == nullptr ||
         timestamp > latest_before_from->basic_info.end_timestamp())) {
      latest_before_from = &sample;
    }
  }

  if (latest_before_from != nullptr) {
    // Add the snapshot to the response
    response.snapshot = latest_before_from->snapshot;
  }

  return response;
}

CpuCache::AppCpuCache* CpuCache::FindAppCache(int32_t app_id) {
  for (auto& cache : app_caches_) {
    if (app_id == cache->app_id) {
      return cache.get();
    }
  }
  return nullptr;
}

}  // namespace profiler
