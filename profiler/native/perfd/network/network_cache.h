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
#ifndef PERFD_NETWORK_NETWORK_CACHE_H_
#define PERFD_NETWORK_NETWORK_CACHE_H_

#include <list>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "perfd/network/connection_details.h"
#include "utils/circular_buffer.h"
#include "utils/file_cache.h"

namespace profiler {

// TODO: NetworkProfilerBuffer belongs in here
// Note: This class is thread safe
// TODO: This class needs tests
class NetworkCache final {
 public:
  NetworkCache();

  // Register a new connection, returning a |ConnectionDetails| instance in case
  // there is additional information you can initialize.
  ConnectionDetails* AddConnection(int64_t conn_id, int32_t app_id,
                                   int64_t start_timestamp);

  // Return details for the request with a matching |conn_id|, or nullptr if no
  // match.
  // A connection will exist only after registered by |AddConnection|, although
  // it may be removed from the cache later, so always check for |nullptr|.
  ConnectionDetails* GetDetails(int64_t conn_id);
  const ConnectionDetails* GetDetails(int64_t conn_id) const;

  // Return a subset of this cache after filtering based on app ID and time
  // range (inclusive). The results will be sorted by start time in ascending
  // order.
  std::vector<ConnectionDetails> GetRange(int32_t app_id,
                                          int64_t start_timestamp,
                                          int64_t end_timestamp) const;
  // TODO: Add RemoveIfOlder(int64_t end_timestamp), call on a thread somewhere
 private:
  // Internal handler for |GetDetails| methods, so both const and non-const
  // versions can delegate to it.
  ConnectionDetails* DoGetDetails(int64_t conn_id) const;

  // Mutex guards connections_ and conn_id_map_
  mutable std::mutex connections_mutex_;
  CircularBuffer<ConnectionDetails> connections_;
  // A mapping of connection IDs to connection details
  std::unordered_map<int64_t, ConnectionDetails*> conn_id_map_;
};

}  // namespace profiler

#endif  // PERFD_NETWORK_NETWORK_CACHE_H_
