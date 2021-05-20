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

#include "daemon/daemon.h"
#include "proto/profiler.pb.h"
#include "utils/process_manager.h"

namespace profiler {

using std::list;
using std::lock_guard;
using std::mutex;
using std::vector;

SessionsManager* SessionsManager::Instance() {
  static SessionsManager* instance = new SessionsManager();
  return instance;
}

void SessionsManager::BeginSession(Daemon* daemon, int64_t stream_id,
                                   int32_t pid,
                                   const proto::BeginSession& data) {
  int64_t now = daemon->clock()->GetCurrentTime();
  if (!sessions_.empty()) {
    DoEndSession(daemon, sessions_.back().get(), now);
  }

  bool unified_pipeline =
      daemon->config()->GetConfig().common().profiler_unified_pipeline();
  if (unified_pipeline) {
    std::string app_name = ProcessManager::GetCmdlineForPid(pid);
    // Drains and sends the queued events.
    auto itr = app_events_queue_.find(app_name);
    if (itr != app_events_queue_.end()) {
      auto queue = itr->second.Drain();
      while (!queue.empty()) {
        auto& event = queue.front();
        event.set_pid(pid);
        daemon->buffer()->Add(event);
        now = std::min(now, event.timestamp());
        queue.pop_front();
      }
    }
  } else {
    for (const auto& component : daemon->GetProfilerComponents()) {
      now = std::min(now, component->GetEarliestDataTime(pid));
    }
  }

  std::unique_ptr<Session> session(new Session(stream_id, pid, now, daemon));
  proto::Event event;
  event.set_pid(pid);
  event.set_group_id(session->info().session_id());
  event.set_timestamp(now);
  event.set_kind(proto::Event::SESSION);
  auto session_data = event.mutable_session();
  auto session_started = session_data->mutable_session_started();
  session_started->set_session_id(session->info().session_id());
  session_started->set_stream_id(stream_id);
  session_started->set_pid(pid);
  session_started->set_start_timestamp_epoch_ms(data.request_time_epoch_ms());
  session_started->set_session_name(data.session_name());
  session_started->set_jvmti_enabled(data.jvmti_config().attach_agent());
  session_started->set_process_abi(data.process_abi());
  session_started->set_type(proto::SessionData::SessionStarted::FULL);
  daemon->buffer()->Add(event);

  sessions_.push_back(std::move(session));
}

profiler::Session* SessionsManager::GetLastSession() {
  if (sessions_.size()) {
    return sessions_.back().get();
  } else {
    return nullptr;
  }
}

void SessionsManager::ClearSessions() { sessions_.clear(); }

void SessionsManager::EndSession(Daemon* daemon, int64_t session_id) {
  auto now = daemon->clock()->GetCurrentTime();
  if (sessions_.size() > 0) {
    if (sessions_.back()->info().session_id() == session_id) {
      DoEndSession(daemon, sessions_.back().get(), now);
    }
  }
}

// This method assumes |sessions_| has already been locked and that
// |session_index| is valid.
void SessionsManager::DoEndSession(Daemon* daemon, profiler::Session* session,
                                   int64_t time) {
  // TODO(b/67508650): Stop all profilers!
  if (session->End(time)) {
    proto::Event event;
    event.set_timestamp(time);
    event.set_pid(session->info().pid());
    event.set_group_id(session->info().session_id());
    event.set_kind(proto::Event::SESSION);
    event.set_is_ended(true);
    daemon->buffer()->Add(event);
  }
}

void SessionsManager::SendOrQueueEventsForSession(
    Daemon* daemon, const std::string& app_name,
    const vector<proto::Event>& events) {
  bool session_is_live = false;
  ProcessManager process_manager;
  int32_t pid = process_manager.GetPidForBinary(app_name);
  Log::D(Log::Tag::PROFILER, "Found pid for '%s': %d", app_name.c_str(), pid);
  if (pid >= 0) {
    for (auto it = sessions_.begin(); it != sessions_.end(); it++) {
      Log::D(Log::Tag::PROFILER, "Session: %d", (*it)->IsActive());
      if ((*it)->info().pid() == pid && (*it)->IsActive()) {
        session_is_live = true;
        break;
      }
    }
  }

  if (session_is_live) {
    for (auto it = events.begin(); it != events.end(); it++) {
      proto::Event event_with_pid;
      event_with_pid.CopyFrom(*it);
      event_with_pid.set_pid(pid);
      daemon->buffer()->Add(event_with_pid);
    }
  } else {
    auto& queue =
        app_events_queue_
            .emplace(std::piecewise_construct, std::forward_as_tuple(app_name),
                     std::forward_as_tuple(-1))  // -1 for unbounded queue.
            .first->second;
    for (auto it = events.begin(); it != events.end(); it++) {
      queue.Push(*it);
    }
  }
}

}  // namespace profiler
