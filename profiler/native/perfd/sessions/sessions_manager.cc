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
#include "perfd/daemon.h"
#include "proto/profiler.pb.h"

namespace profiler {

using std::list;
using std::lock_guard;
using std::mutex;
using std::vector;

void SessionsManager::BeginSession(int64_t device_id,
                                   const proto::BeginSession& data) {
  int64_t now = daemon_->clock()->GetCurrentTime();
  int32_t pid = data.pid();
  for (const auto& component : daemon_->GetComponents()) {
    now = std::min(now, component->GetEarliestDataTime(pid));
  }

  if (!sessions_.empty()) {
    DoEndSession(sessions_.back().get(), now);
  }

  std::unique_ptr<Session> session(new Session(device_id, pid, now, daemon_));
  proto::Event event;
  event.set_group_id(session->info().session_id());
  event.set_session_id(session->info().session_id());
  event.set_timestamp(now);
  event.set_kind(proto::Event::SESSION);
  event.set_type(proto::Event::SESSION_STARTED);
  proto::SessionStarted* session_started = event.mutable_session_started();
  session_started->set_pid(pid);
  session_started->set_start_timestamp_epoch_ms(data.request_time_epoch_ms());
  session_started->set_session_name(data.session_name());
  session_started->set_jvmti_enabled(data.jvmti_config().attach_agent());
  session_started->set_live_allocation_enabled(
      data.jvmti_config().live_allocation_enabled());
  session_started->set_type(proto::SessionStarted::FULL);
  daemon_->buffer()->Add(event);

  sessions_.push_back(std::move(session));
}

profiler::Session* SessionsManager::GetLastSession() {
  if (sessions_.size()) {
    return sessions_.back().get();
  } else {
    return nullptr;
  }
}

void SessionsManager::EndSession(int64_t session_id) {
  auto now = daemon_->clock()->GetCurrentTime();
  if (sessions_.size() > 0) {
    if (sessions_.back()->info().session_id() == session_id) {
      DoEndSession(sessions_.back().get(), now);
    }
  }
}

// This method assumes |sessions_| has already been locked and that
// |session_index| is valid.
void SessionsManager::DoEndSession(profiler::Session* session, int64_t time) {
  // TODO(b/67508650): Stop all profilers!
  if (session->End(time)) {
    proto::Event event;
    event.set_timestamp(time);
    event.set_group_id(session->info().session_id());
    event.set_session_id(session->info().session_id());
    event.set_kind(proto::Event::SESSION);
    event.set_type(proto::Event::SESSION_ENDED);
    event.mutable_session_ended();
    daemon_->buffer()->Add(event);
  }
}

}  // namespace profiler
