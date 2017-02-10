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

void CpuCache::Add(const CpuProfilerData& datum) {
  std::lock_guard<std::mutex> lock(cache_mutex_);
  cache_.push_back(datum);
}

vector<CpuProfilerData> CpuCache::Retrieve(int32_t app_id, int64_t from,
                                           int64_t to) {
  std::lock_guard<std::mutex> lock(cache_mutex_);
  vector<CpuProfilerData> filtered;

  for (const auto& datum : cache_) {
    auto id = datum.basic_info().process_id();
    auto timestamp = datum.basic_info().end_timestamp();
    if (id == app_id || app_id == proto::AppId::ANY) {
      if (timestamp > from && timestamp <= to) {
        filtered.push_back(datum);
      }
    }
  }

  return filtered;
}

void CpuCache::AddThreads(const ThreadsSample& threads_sample) {
  std::lock_guard<std::mutex> lock(threads_cache_mutex_);
  threads_cache_.push_back(threads_sample);
}

CpuCache::ThreadSampleResponse CpuCache::GetThreads(int32_t app_id,
                                                    int64_t from, int64_t to) {
  std::lock_guard<std::mutex> lock(threads_cache_mutex_);
  CpuCache::ThreadSampleResponse response;
  const ThreadsSample* latest_before_from = nullptr;
  // TODO: optimize it to binary search the initial point. That will also make
  // it easier to get the data from the greatest timestamp smaller than |from|.
  for (const auto& sample : threads_cache_) {
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

}  // namespace profiler
