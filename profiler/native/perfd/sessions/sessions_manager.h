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
#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "daemon/daemon.h"
#include "perfd/sessions/session.h"
#include "proto/common.pb.h"
#include "proto/profiler.grpc.pb.h"
#include "utils/producer_consumer_queue.h"

namespace profiler {

class SessionsManager final {
 public:
  // Single instance shared across all profilers.
  static SessionsManager *Instance();

  // Begins a new session. If a session was
  // already running it will be ended.
  void BeginSession(Daemon *daemon, int64_t stream_id, int32_t pid,
                    const proto::BeginSession &data);

  // Ends the given session if it was active.
  void EndSession(Daemon *daemon, int64_t session_id);

  // Returns the last session (which is the only one that can be active),
  // or nullptr if there are none.
  profiler::Session *GetLastSession();

  // Clears all sessions.
  // Visible for testing.
  void ClearSessions();

  // If a session associated with |app_name| is alive, send the events
  // immeidately. Otherwise, the events will be queued and sent when the
  // session is created.
  void SendOrQueueEventsForSession(Daemon *daemon, const std::string &app_name,
                                   const std::vector<proto::Event> &events);

 private:
  SessionsManager() = default;
  void DoEndSession(Daemon *daemon, profiler::Session *session,
                    int64_t timestamp_ns);

  std::vector<std::unique_ptr<profiler::Session>> sessions_;
  std::map<std::string, ProducerConsumerQueue<proto::Event>> app_events_queue_;
};

}  // namespace profiler

#endif  // PERFD_SESSIONS_SESSIONS_MANAGER_H
