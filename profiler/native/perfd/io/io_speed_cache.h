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
#ifndef PERFD_IO_IO_SPEED_CACHE_H_
#define PERFD_IO_IO_SPEED_CACHE_H_

#include <memory>

#include "perfd/io/io_speed_app_cache.h"
#include "perfd/io/io_speed_details.h"

namespace profiler {

class IoSpeedCache final {
 public:
  // Returns true if successfully allocating a cache for a given app, or if
  // the cache is already allocated.
  bool AllocateAppCache(int32_t app_id);
  // Returns true if successfully deallocating the cache for a given app.
  bool DeallocateAppCache(int32_t app_id);

  // Adds the I/O call to the cache
  void AddIoCall(int32_t app_id, int64_t start_timestamp, int64_t end_timestamp,
                 int32_t bytes_count, profiler::proto::IoType type);

  // Returns speed data for the given app id and interval
  std::vector<IoSpeedDetails> GetSpeedData(int32_t app_id,
                                           int64_t start_timestamp,
                                           int64_t end_timestamp,
                                           profiler::proto::IoType type) const;

 private:
  std::vector<std::unique_ptr<IoSpeedAppCache>> app_caches_;
  // Returns the raw pointer to the cache for a given app. Returns nullptr if
  // it doesn't exist.
  IoSpeedAppCache* FindAppCache(int32_t app_id) const;
};

}  // namespace profiler

#endif  // PERFD_IO_IO_SPEED_CACHE_H_
