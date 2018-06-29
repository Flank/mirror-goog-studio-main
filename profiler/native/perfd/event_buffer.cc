/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "perfd/event_buffer.h"

namespace profiler {

void EventBuffer::Add(proto::Event& event) {
  std::lock_guard<std::mutex> lock(mutex_);
  events_.Add(event);
  // TODO(b/73538507): optimize this:
  proto::EventGroup* group = nullptr;
  for (size_t i = 0; i < groups_.size(); i++) {
    auto& g = groups_.Get(i);
    if (g.event_id() == event.event_id()) {
      group = &g;
      break;
    }
  }
  if (!group) {
    groups_.Add(proto::EventGroup());
    group = &groups_.back();
    group->set_event_id(event.event_id());
  }
  group->add_events()->CopyFrom(event);
}

std::vector<proto::Event> EventBuffer::Get(int64_t from, int64_t to) {
  std::lock_guard<std::mutex> lock(mutex_);
  std::vector<proto::Event> result;
  for (size_t i = 0; i < events_.size(); i++) {
    const auto& value = events_.Get(i);
    if (value.timestamp() >= from && value.timestamp() <= to) {
      result.push_back(value);
    }
  }
  return result;
}

std::vector<proto::EventGroup> EventBuffer::Get(int64_t session_id,
                                                proto::Event::Kind kind,
                                                proto::Event::Type end,
                                                int64_t from, int64_t to) {
  std::lock_guard<std::mutex> lock(mutex_);
  std::set<int64_t> event_ids;
  for (size_t i = 0; i < events_.size(); i++) {
    const auto& event = events_.Get(i);
    if (event.kind() == kind) {
      if (event.timestamp() < from) {
        if (event.type() == end) {
          event_ids.erase(event.event_id());
        } else {
          event_ids.insert(event.event_id());
        }
      } else if (event.timestamp() <= to) {
        event_ids.insert(event.event_id());
      }
    }
  }

  std::vector<proto::EventGroup> groups;
  for (size_t i = 0; i < groups_.size(); i++) {
    const auto& g = groups_.Get(i);
    if (event_ids.count(g.event_id())) {
      groups.push_back(g);
    }
  }

  return groups;
}

bool EventBuffer::GetGroup(int64_t event_id, proto::EventGroup* group) {
  std::lock_guard<std::mutex> lock(mutex_);
  for (size_t i = 0; i < groups_.size(); i++) {
    const auto& g = groups_.Get(i);
    if (g.event_id() == event_id) {
      group->CopyFrom(g);
      return true;
    }
  }
  return false;
}
}  // namespace profiler
