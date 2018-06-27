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
#include <functional>
#include <list>
#include <mutex>
#include <string>
#include <vector>

#include "proto/common.pb.h"
#include "utils/clock.h"

namespace profiler {

// Note: This class is thread safe
class SessionsManager final {
 public:
  SessionsManager(Clock *clock) : clock_(clock) {}

  // Begins a new session and populates it in |session|. If a session was
  // already running it will be ended.
  void BeginSession(int64_t device_id, int32_t pid, proto::Session *session,
                    int64_t start_timestamp);
  // Ends the active session and populates |session| with it.
  void EndSession(int64_t session_id, proto::Session *session);

  // Fills in session with with the session of the given id
  void GetSession(int64_t session_id, proto::Session *session) const;

  // Returns all sessions between two timestamps. Default values are provided so
  // if you exclude a timestamp, then the search will not be bounded by it.
  // |start_timestamp| should always be <= |end_timestamp|, or else this
  // method's behavior will be undefined.
  std::vector<proto::Session> GetSessions(
      int64_t start_timestamp = LLONG_MIN,
      int64_t end_timestamp = LLONG_MAX) const;

 private:
  void DoEndSession(proto::Session *session);

  Clock *clock_;

  mutable std::mutex sessions_mutex_;

  std::vector<proto::Session> sessions_;
};

}  // namespace profiler

#endif  // PERFD_SESSIONS_SESSIONS_MANAGER_H
