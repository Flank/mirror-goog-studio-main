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

#include "perfd/sessions/session.h"
#include "proto/common.pb.h"

namespace profiler {

class Daemon;

class SessionsManager final {
 public:
  SessionsManager(Daemon *daemon) : daemon_(daemon) {}

  // Begins a new session. If a session was
  // already running it will be ended.
  void BeginSession(int64_t device_id, int32_t pid);

  // Returns the last session (which is the only one that can be active),
  // or nullptr if there are none.
  profiler::Session *GetLastSession();

  // Ends the given session if it was active.
  void EndSession(int64_t session_id);

 private:
  void DoEndSession(profiler::Session *session, int64_t timestamp_ns);

  Daemon *daemon_;

  std::vector<std::unique_ptr<profiler::Session>> sessions_;
};

}  // namespace profiler

#endif  // PERFD_SESSIONS_SESSIONS_MANAGER_H
