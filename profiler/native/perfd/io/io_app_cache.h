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
#ifndef PERFD_IO_IO_APP_CACHE_H_
#define PERFD_IO_IO_APP_CACHE_H_

#include <mutex>
#include <unordered_map>

#include "perfd/io/session_details.h"
#include "utils/circular_buffer.h"

namespace profiler {

class IoAppCache final {
 public:
  explicit IoAppCache(int32_t app_id);

  // Register a new I/O session, returning a |SessionDetails| instance.
  SessionDetails* AddSession(int64_t session_id, int64_t timestamp,
                             std::string file_path);

  // Return details for the session with a matching |session_id|, or nullptr if
  // there is no match. A session will exist only after registered by
  // |AddSession|, although it may be removed from the cache later, so always
  // check for |nullptr|.
  SessionDetails* GetDetails(int64_t session_id);

  // Return a subset of this cache after filtering based on time range
  // (inclusive). The results will be sorted by start time in ascending order.
  std::vector<SessionDetails> GetRange(int64_t start_timestamp,
                                       int64_t end_timestamp) const;
  // Returns the app id whose data is being saved in this cache object.
  int32_t app_id() const { return app_id_; }

 private:
  const int32_t app_id_;
  // Mutex guards sessions_ and session_id_map_
  mutable std::mutex sessions_mutex_;
  CircularBuffer<SessionDetails> sessions_;
  // A mapping of sessions IDs to session data
  std::unordered_map<int64_t, SessionDetails*> session_id_map_;
};

}  // namespace profiler

#endif  // PERFD_IO_IO_APP_CACHE_H_
