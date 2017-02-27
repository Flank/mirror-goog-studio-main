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

using std::lock_guard;
using std::mutex;
using std::vector;

namespace profiler {

NetworkCache::NetworkCache() : connections_(1000) {}

ConnectionDetails* NetworkCache::AddConnection(int64_t conn_id, int32_t app_id,
                                               int64_t start_timestamp) {
  if (connections_.full()) {
    // An old connection is about to get overwritten, so remove it from our map
    const auto& conn = connections_.Get(0);
    conn_id_map_.erase(conn.id);
  }

  ConnectionDetails new_conn;
  new_conn.id = conn_id;
  new_conn.app_id = app_id;
  new_conn.start_timestamp = start_timestamp;

  lock_guard<mutex> lock(connections_mutex_);
  // |Add| copies new_conn; instead of tracking the (temporary) original, make
  // sure we use the address of the *copy* instead.
  ConnectionDetails* conn_ptr = connections_.Add(new_conn);
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
  for (size_t i = 0; i < connections_.size(); ++i) {
    const auto& conn = connections_.Get(i);
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
