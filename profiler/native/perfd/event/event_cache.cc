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
#include "event_cache.h"

#include <iterator>
#include <vector>

#include "utils/log.h"

using profiler::EventCache;
using profiler::proto::EventProfilerData;
using profiler::proto::EventDataResponse;
using std::lock_guard;
using std::vector;

namespace profiler {

using proto::EventProfilerData;

void EventCache::AddData(const EventProfilerData& data) {
  // TODO: Add sorting function to vector / priority queue to guarantee
  // chronological sorting.
  lock_guard<std::mutex> lock(cache_mutex_);
  cache_.push_back(data);
}

void EventCache::GetData(int64_t start_time, int64_t end_time,
                         EventDataResponse* response) {
  lock_guard<std::mutex> lock(cache_mutex_);
  // TODO: Binary Search to find the start index, and end index.
  for (const auto& data : cache_) {
    int64_t time = data.basic_info().end_timestamp();
    if (time > start_time && time <= end_time) {
      EventProfilerData* out_data = response->add_data();
      out_data->CopyFrom(data);
    }
  }
}

}  // namespace profiler
