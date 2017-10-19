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
#include "io_app_cache.h"

using std::lock_guard;
using std::mutex;
using std::string;
using std::vector;

namespace profiler {
// 1000 sessions per app means 1000 objects operation on files at the same time.
IoAppCache::IoAppCache(int32_t app_id) : app_id_(app_id), sessions_(1000) {}

IoSessionDetails* IoAppCache::AddSession(int64_t session_id, int64_t timestamp,
                                         string file_path) {
  lock_guard<mutex> lock(sessions_mutex_);
  if (sessions_.full()) {
    // An old session is about to get overwritten, so remove it from our map
    const auto& session = sessions_.Get(0);
    session_id_map_.erase(session.session_id);
  }

  IoSessionDetails new_session;
  new_session.session_id = session_id;
  new_session.start_timestamp = timestamp;
  new_session.file_path = file_path;
  // |Add| copies new_session; instead of tracking the (temporary) original,
  // make sure we use the address of the *copy* instead.
  IoSessionDetails* session_ptr = sessions_.Add(new_session);
  session_id_map_[session_id] = session_ptr;
  return session_ptr;
}

IoSessionDetails* IoAppCache::GetDetails(int64_t session_id) {
  lock_guard<mutex> lock(sessions_mutex_);
  auto it = session_id_map_.find(session_id);
  if (it != session_id_map_.end()) {
    return it->second;
  } else {
    return nullptr;
  }
}

vector<IoSessionDetails> IoAppCache::GetRange(int64_t start,
                                              int64_t end) const {
  lock_guard<mutex> lock(sessions_mutex_);
  vector<IoSessionDetails> data_range;
  for (size_t i = 0; i < sessions_.size(); ++i) {
    const auto& session = sessions_.Get(i);
    // Given a range t0 and t1 and sessions a-f...
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

    if (end < session.start_timestamp) {
      break;  // Eliminate sessions like f (and all sessions after)
    }

    // At this point: session.start_timestamp <= end
    if (session.end_timestamp != -1 && session.end_timestamp <= start) {
      continue;  // Eliminate sessions like b
    }

    data_range.push_back(session);
  }

  return data_range;
}

}  // namespace profiler
