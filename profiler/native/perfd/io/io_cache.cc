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
#include "io_cache.h"

namespace profiler {

bool IoCache::AllocateAppCache(int32_t app_id) {
  if (FindAppCache(app_id) != nullptr) return true;
  app_caches_.emplace_back(new IoAppCache(app_id));
  return true;
}

bool IoCache::DeallocateAppCache(int32_t app_id) {
  for (auto it = app_caches_.begin(); it != app_caches_.end(); it++) {
    if (app_id == (*it)->app_id()) {
      app_caches_.erase(it);
      return true;
    }
  }
  return false;
}

SessionDetails* IoCache::AddSession(int32_t app_id, int64_t session_id,
                                    int64_t timestamp, std::string file_path) {
  IoAppCache* io_app_cache = FindAppCache(app_id);
  if (io_app_cache == nullptr) {
    return nullptr;
  }
  return io_app_cache->AddSession(session_id, timestamp, file_path);
}

SessionDetails* IoCache::GetDetails(int32_t app_id, int64_t session_id) {
  IoAppCache* io_app_cache = FindAppCache(app_id);
  if (io_app_cache == nullptr) {
    return nullptr;
  }
  return io_app_cache->GetDetails(session_id);
}

std::vector<SessionDetails> IoCache::GetRange(int32_t app_id, int64_t start,
                                              int64_t end) const {
  IoAppCache* io_app_cache = FindAppCache(app_id);
  if (io_app_cache == nullptr) {
    std::vector<SessionDetails> empty;
    return empty;
  }
  return io_app_cache->GetRange(start, end);
}

IoAppCache* IoCache::FindAppCache(int32_t app_id) const {
  for (const auto& cache : app_caches_) {
    if (app_id == cache->app_id()) {
      return cache.get();
    }
  }
  return nullptr;
}

}  // namespace profiler
