/*
 * Copyright (C) 2017 The Android Open Source Project
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
#include "io_speed_cache.h"

namespace profiler {

bool IoSpeedCache::AllocateAppCache(int32_t app_id) {
  if (FindAppCache(app_id) != nullptr) return true;
  app_caches_.emplace_back(new IoSpeedAppCache(app_id));
  return true;
}
// Returns true if successfully deallocating the cache for a given app.
bool IoSpeedCache::DeallocateAppCache(int32_t app_id) {
  for (auto it = app_caches_.begin(); it != app_caches_.end(); it++) {
    if (app_id == (*it)->app_id()) {
      app_caches_.erase(it);
      return true;
    }
  }
  return false;
}

// Adds the I/O call to the cache
void IoSpeedCache::AddIoCall(int32_t app_id, int64_t start_timestamp,
                             int64_t end_timestamp, int32_t bytes_count,
                             profiler::proto::IoType type) {
  IoSpeedAppCache* io_speed_app_cache = FindAppCache(app_id);
  if (io_speed_app_cache == nullptr) {
    return;
  }
  io_speed_app_cache->AddIoCall(start_timestamp, end_timestamp, bytes_count,
                                type);
}

// Returns speed data for the given app id and interval
std::vector<IoSpeedDetails> IoSpeedCache::GetSpeedData(
    int32_t app_id, int64_t start_timestamp, int64_t end_timestamp,
    profiler::proto::IoType type) const {
  IoSpeedAppCache* io_speed_app_cache = FindAppCache(app_id);
  if (io_speed_app_cache == nullptr) {
    std::vector<IoSpeedDetails> empty;
    return empty;
  }
  return io_speed_app_cache->GetSpeedData(start_timestamp, end_timestamp, type);
}

IoSpeedAppCache* IoSpeedCache::FindAppCache(int32_t app_id) const {
  for (const auto& cache : app_caches_) {
    if (app_id == cache->app_id()) {
      return cache.get();
    }
  }
  return nullptr;
}

}  // namespace profiler