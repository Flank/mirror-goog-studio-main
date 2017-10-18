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
#ifndef PERFD_SESSIONS_SESSION_UTILS_H
#define PERFD_SESSIONS_SESSION_UTILS_H

#include "proto/profiler.pb.h"

#include <climits>

namespace profiler {

// Various static utility methods that work with the |Session| proto object
class SessionUtils final {
 public:
  static proto::Session CreateSession(const std::string& device_serial,
                                      const std::string& boot_id, int32_t pid,
                                      int64_t start_timestamp) {
    proto::Session session;
    // TODO(b/67508221): Generate a better session ID, something that is hashed
    // so that each ID looks very different from other IDs
    int64_t session_id = start_timestamp;
    session.set_session_id(session_id);

    session.set_device_serial(device_serial);
    session.set_boot_id(boot_id);
    session.set_pid(pid);
    session.set_start_timestamp(start_timestamp);
    session.set_end_timestamp(LLONG_MAX);
    return session;
  }

  static bool IsActive(const proto::Session& session) {
    return session.end_timestamp() == LLONG_MAX;
  }

 private:
  SessionUtils() = delete;
};

}  // namespace profiler

#endif  // PERFD_SESSIONS_SESSION_UTILS_H
