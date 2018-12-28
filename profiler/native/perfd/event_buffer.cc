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
#include "perfd/event_writer.h"

#include "utils/log.h"

namespace profiler {

void EventBuffer::Add(proto::Event& event) {
  std::unique_lock<std::mutex> lock(mutex_);
  events_added_++;
  event.set_timestamp(clock_->GetCurrentTime());
  events_.Add(event);
  // TODO(b/73538507): optimize this:
  proto::EventGroup* group = nullptr;
  for (size_t i = 0; i < groups_.size(); i++) {
    auto& g = groups_.Get(i);
    if (g.group_id() == event.group_id()) {
      group = &g;
      break;
    }
  }
  if (!group) {
    groups_.Add(proto::EventGroup());
    group = &groups_.back();
    group->set_group_id(event.group_id());
  }
  group->add_events()->CopyFrom(event);
  events_cv_.notify_all();
}

void EventBuffer::WriteEventsTo(EventWriter* writer) {
  while (!interrupt_write_) {
    // Write any events that may have queued before our event listener has
    // connected.
    std::unique_lock<std::mutex> lock(mutex_);
    // Check if we are slower sending events than we are adding new events.
    // If so we log a warning and clamp the events to the size of our ring
    // buffer.
    if (events_added_ > events_.size()) {
      Log::W("Writing events thread missed sending %d events.",
             (int)(events_added_ - events_.size()));
      events_added_ = events_.size();
    }
    while (events_added_ > 0) {
      bool success = writer->Write(events_.Get(events_.size() - events_added_));
      events_added_--;
      // If we fail to send data to a client.
      if (!success) {
        return;
      }
    }
    events_cv_.wait_for(lock, std::chrono::milliseconds(500), [this] {
      return interrupt_write_ || events_added_ > 0;
    });
  }
}

void EventBuffer::InterruptWriteEvents() {
  interrupt_write_ = true;
  events_cv_.notify_all();
}

std::vector<proto::EventGroup> EventBuffer::Get(proto::Event::Kind kind,
                                                int64_t from, int64_t to) {
  std::lock_guard<std::mutex> lock(mutex_);
  std::set<int64_t> group_ids;
  for (size_t i = 0; i < groups_.size(); i++) {
    const auto& g = groups_.Get(i);
    for (int j = 0; j < g.events_size(); j++) {
      const auto& event = g.events(j);
      if (event.kind() == kind) {
        if (event.timestamp() < from) {
          if (event.is_ended()) {
            group_ids.erase(event.group_id());
          } else {
            group_ids.insert(event.group_id());
          }
        } else if (event.timestamp() <= to) {
          group_ids.insert(event.group_id());
        }
      }
    }
  }

  std::vector<proto::EventGroup> groups;
  for (size_t i = 0; i < groups_.size(); i++) {
    const auto& g = groups_.Get(i);
    if (group_ids.count(g.group_id())) {
      groups.push_back(g);
    }
  }

  return groups;
}

bool EventBuffer::GetGroup(int64_t group_id, proto::EventGroup* group) {
  std::lock_guard<std::mutex> lock(mutex_);
  for (size_t i = 0; i < groups_.size(); i++) {
    const auto& g = groups_.Get(i);
    if (g.group_id() == group_id) {
      group->CopyFrom(g);
      return true;
    }
  }
  return false;
}
}  // namespace profiler
