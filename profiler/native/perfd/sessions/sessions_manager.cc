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
#include "sessions_manager.h"

#include <algorithm>

#include "perfd/sessions/session_utils.h"

namespace profiler {

using proto::Session;
using std::list;
using std::lock_guard;
using std::mutex;
using std::vector;

bool SessionsManager::BeginSession(int64_t device_id, int32_t pid,
                                   Session* session) {
  lock_guard<mutex> lock(sessions_mutex_);
  auto it =
      std::find_if(sessions_.begin(), sessions_.end(), [&](const Session& s) {
        return s.device_id() == device_id && s.pid() == pid &&
               SessionUtils::IsActive(s);
      });

  bool new_session = false;
  if (it == sessions_.end()) {
    sessions_.push_front(
        SessionUtils::CreateSession(device_id, pid, clock_->GetCurrentTime()));
    new_session = true;
  } else {
    // If a matching session was already running, move it to the front of the
    // list (since it now should be the most recent), unless it's already in
    // the front.
    if (it != sessions_.begin()) {
      sessions_.splice(sessions_.begin(), sessions_, it, std::next(it));
    }
  }

  session->CopyFrom(sessions_.front());
  return new_session;
}

bool SessionsManager::EndSession(int64_t session_id, Session* session) {
  lock_guard<mutex> lock(sessions_mutex_);
  auto it = GetSessionIter({[session_id](const Session& s) {
    return s.session_id() == session_id;
  }});
  if (it != sessions_.end()) {
    DoEndSession(&(*it));
    session->CopyFrom(*it);
    return true;
  } else {
    return false;
  }
}

bool SessionsManager::GetSession(int64_t session_id, Session* session) const {
  lock_guard<mutex> lock(sessions_mutex_);
  auto it = GetSessionIter({[session_id](const Session& s) {
    return s.session_id() == session_id;
  }});
  if (it != sessions_.end()) {
    session->CopyFrom(*it);
    return true;
  } else {
    return false;
  }
}

bool SessionsManager::GetActiveSessionByPid(int32_t pid,
                                            Session* session) const {
  lock_guard<mutex> lock(sessions_mutex_);
  auto it = GetSessionIter({[pid](const Session& s) {
    return s.pid() == pid && SessionUtils::IsActive(s);
  }});
  if (it != sessions_.end()) {
    session->CopyFrom(*it);
    return true;
  } else {
    return false;
  }
}

std::vector<Session> SessionsManager::GetSessions(int64_t start_timestamp,
                                                  int64_t end_timestamp) const {
  lock_guard<mutex> lock(sessions_mutex_);
  vector<Session> sessions_range;
  for (const auto& session : sessions_) {
    if (end_timestamp < session.start_timestamp() ||
        start_timestamp > session.end_timestamp()) {
      continue;
    }
    sessions_range.push_back(session);
  }
  return sessions_range;
}

// This method assumes |sessions_| has already been locked
list<Session>::iterator SessionsManager::GetSessionIter(
    const std::function<bool(const Session& s)>& match_func) {
  return std::find_if(sessions_.begin(), sessions_.end(), match_func);
}

// This method assumes |sessions_| has already been locked
list<Session>::const_iterator SessionsManager::GetSessionIter(
    const std::function<bool(const Session& s)>& match_func) const {
  return std::find_if(sessions_.begin(), sessions_.end(), match_func);
}

// This method assumes |sessions_| has already been locked and that
// |session_index| is valid.
void SessionsManager::DoEndSession(Session* session) {
  if (!SessionUtils::IsActive(*session)) {
    return;
  }

  session->set_end_timestamp(clock_->GetCurrentTime());
  // TODO(b/67508650): Stop all profilers!
}

}  // namespace profiler
