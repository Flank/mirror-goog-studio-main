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

void SessionsManager::BeginSession(int64_t device_id, int32_t pid,
                                   Session* session, int64_t start_timestamp) {
  lock_guard<mutex> lock(sessions_mutex_);
  if (sessions_.size()) {
    Session& last = sessions_[sessions_.size() - 1];
    if (SessionUtils::IsActive(last)) {
      DoEndSession(&last);
    }
  }

  Session new_session =
      SessionUtils::CreateSession(device_id, pid, start_timestamp);
  sessions_.push_back(new_session);
  session->CopyFrom(new_session);
}

void SessionsManager::EndSession(int64_t session_id, Session* session) {
  lock_guard<mutex> lock(sessions_mutex_);

  if (sessions_.size()) {
    Session& last = sessions_[sessions_.size() - 1];
    if (SessionUtils::IsActive(last) && last.session_id() == session_id) {
      DoEndSession(&last);
      session->CopyFrom(last);
    }
  }
}

void SessionsManager::GetSession(int64_t session_id, Session* session) const {
  lock_guard<mutex> lock(sessions_mutex_);
  for (const Session& s : sessions_) {
    if (s.session_id() == session_id) {
      session->CopyFrom(s);
    }
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
void SessionsManager::DoEndSession(Session* session) {
  session->set_end_timestamp(clock_->GetCurrentTime());
  // TODO(b/67508650): Stop all profilers!
}

}  // namespace profiler
