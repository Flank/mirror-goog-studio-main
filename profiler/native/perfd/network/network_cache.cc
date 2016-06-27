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
#include "network_cache.h"

namespace profiler {

using std::back_inserter;
using std::lock_guard;
using std::mutex;
using std::vector;

NetworkCache::NetworkCache(const Clock& clock) : clock_(clock) {}

ConnectionDetails* NetworkCache::AddConnection(int64_t conn_id,
                                               int32_t app_id) {
  ConnectionDetails new_conn;
  new_conn.id = conn_id;
  new_conn.app_id = app_id;
  new_conn.start_timestamp = clock_.GetCurrentTime();

  lock_guard<mutex> lock(connections_mutex_);
  connections_.push_back(new_conn);
  // The above line copies new_conn; instead of returning "new_conn" below, make
  // sure we pass the address of the *copy* instead.
  ConnectionDetails* conn_ptr = &connections_.back();
  conn_id_map_[conn_id] = conn_ptr;
  return conn_ptr;
}

ConnectionDetails* NetworkCache::GetDetails(int64_t conn_id) {
  return DoGetDetails(conn_id);
}

const ConnectionDetails* NetworkCache::GetDetails(int64_t conn_id) const {
  return DoGetDetails(conn_id);
}

vector<ConnectionDetails> NetworkCache::GetRange(int32_t app_id, int64_t start,
                                                 int64_t end) const {
  lock_guard<mutex> lock(connections_mutex_);
  vector<ConnectionDetails> data_range;
  for (const auto& conn : connections_) {
    if (conn.app_id != app_id) continue;

    // Given a range t0 and t1 and requests a-f...
    //
    //               t0              t1
    // a: [===========|===============|=========...
    // b: [=======]   |               |
    // c:         [===|===]           |
    // d:             |   [=======]   |
    // e:             |           [===|===]
    // f:             |               |   [=======]
    //
    // Keep a, c, d, and e; exclude b and f

    if (end < conn.start_timestamp) {
      break;  // Eliminate requests like f (and all requests after)
    }

    // At this point: conn.start_timestamp <= end
    if (conn.end_timestamp != 0 && conn.end_timestamp < start) {
      continue;  // Eliminate requests like b
    }

    data_range.push_back(conn);
  }

  return data_range;
}

ConnectionDetails* NetworkCache::DoGetDetails(int64_t conn_id) const {
  lock_guard<mutex> lock(connections_mutex_);
  auto it = conn_id_map_.find(conn_id);
  if (it != conn_id_map_.end()) {
    return it->second;
  } else {
    return nullptr;
  }
}

}  // namespace profiler
