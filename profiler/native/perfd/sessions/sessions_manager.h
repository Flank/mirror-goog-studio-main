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
#ifndef PERFD_SESSIONS_SESSIONS_MANAGER_H
#define PERFD_SESSIONS_SESSIONS_MANAGER_H

#include <climits>
#include <list>
#include <mutex>
#include <string>
#include <vector>

#include "proto/profiler.pb.h"
#include "utils/clock.h"

namespace profiler {

// This class manages a list of zero or more sessions, sorted by time created.
// The most recent session may or may not be active.
// Note: This class is thread safe
class SessionsManager final {
 public:
  SessionsManager(const Clock &clock) : clock_(clock) {}

  proto::Session *BeginSession(const std::string &device_serial,
                               const std::string &boot_id, int32_t pid);
  void EndSession(int64_t session_id);

  // Return the session matching the |session_id|, or |nullptr| if not found.
  const proto::Session *GetSession(int64_t session_id) const;

  // Return all sessions between two timestamps. Default values are provided so
  // if you exclude a timestamp, then the search will not be bounded by it.
  // |start_timestamp| should always be <= |end_timestamp|, or else this
  // method's behavior will be undefined.
  std::vector<proto::Session> GetSessions(
      int64_t start_timestamp = LLONG_MIN,
      int64_t end_timestamp = LLONG_MAX) const;

  // Delete the session matching the |session_id|, if found. This will also end
  // the session, if it happens to be active.
  void DeleteSession(int64_t session_id);

 private:
  // Returns the iterator pointing at the session in |sessions_|, or
  // |sessions_.end()| if not found. For internal use only, and expects that
  // the mutex is already locked.
  std::list<proto::Session>::iterator GetSessionIter(int64_t session_id);
  std::list<proto::Session>::const_iterator GetSessionIter(
      int64_t session_id) const;

  // Internally handle ending a session. For internal use only, and expects
  // that the mutex is already locked.
  void DoEndSession(proto::Session *session);

  const Clock &clock_;

  mutable std::mutex sessions_mutex_;
  // Sessions are sorted from most recent to least recent.
  std::list<proto::Session> sessions_;
};

}  // namespace profiler

#endif  // PERFD_SESSIONS_SESSIONS_MANAGER_H
