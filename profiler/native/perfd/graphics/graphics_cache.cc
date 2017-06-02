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
#include "graphics_cache.h"

#include "utils/log.h"

using profiler::proto::GraphicsDataResponse;
using profiler::proto::GraphicsData;

namespace profiler {

GraphicsCache::GraphicsCache(const Clock &clock, int32_t samples_capacity)
    : clock_(clock), graphics_samples_(samples_capacity) {}

void GraphicsCache::SaveGraphicsDataVector(
    const std::vector<GraphicsData> data_vector) {
  int64_t current_time(clock_.GetCurrentTime());
  std::lock_guard<std::mutex> graphics_lock(graphics_samples_mutex_);
  for (auto &data : data_vector) {
    GraphicsData *new_sample = graphics_samples_.Add(data);
    new_sample->mutable_basic_info()->set_end_timestamp(current_time);
  }
}

void GraphicsCache::LoadGraphicsData(int64_t start_time_exl,
                                     int64_t end_time_inc,
                                     GraphicsDataResponse *response) {
  std::lock_guard<std::mutex> graphics_lock(graphics_samples_mutex_);
  for (size_t i = 0; i < graphics_samples_.size(); ++i) {
    const auto &data = graphics_samples_.Get(i);
    int64_t timestamp = data.basic_info().end_timestamp();
    // TODO add optimization to skip past already-queried entries if the array
    // gets large.
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_data()->CopyFrom(data);
    }
  }
}

}  // namespace profiler
