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
#ifndef PERFD_IO_IO_CACHE_H_
#define PERFD_IO_IO_CACHE_H_

#include "perfd/io/io_app_cache.h"
#include "perfd/io/io_session_details.h"

namespace profiler {

class IoCache final {
 public:
  // Returns true if successfully allocating a cache for a given app, or if
  // the cache is already allocated.
  bool AllocateAppCache(int32_t app_id);
  // Returns true if successfully deallocating the cache for a given app.
  bool DeallocateAppCache(int32_t app_id);

  // Register a new I/O session, returning a |IoSessionDetails| instance.
  IoSessionDetails* AddSession(int32_t app_id, int64_t session_id,
                               int64_t timestamp, std::string file_path);

  // Return details for the session with a matching |session_id| and |app_id|,
  // or nullptr if there is no match. A session will exist only after registered
  // by |AddSession|, although it may be removed from the cache later, so always
  // check for |nullptr|.
  IoSessionDetails* GetDetails(int32_t app_id, int64_t session_id);

  // Return a subset of this cache after filtering based on app ID and time
  // range (inclusive). The results will be sorted by start time in ascending
  // order.
  std::vector<IoSessionDetails> GetRange(int32_t app_id,
                                         int64_t start_timestamp,
                                         int64_t end_timestamp) const;

 private:
  std::vector<std::unique_ptr<IoAppCache>> app_caches_;
  // Returns the raw pointer to the cache for a given app. Returns nullptr if
  // it doesn't exist.
  IoAppCache* FindAppCache(int32_t app_id) const;
};

}  // namespace profiler

#endif  // PERFD_IO_IO_CACHE_H_
