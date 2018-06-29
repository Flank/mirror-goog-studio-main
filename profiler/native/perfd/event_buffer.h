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
#ifndef PERFD_EVENT_BUFFER_H_
#define PERFD_EVENT_BUFFER_H_

#include <mutex>

#include "proto/profiler.grpc.pb.h"
#include "utils/circular_buffer.h"

namespace profiler {

// This class is thread safe
class EventBuffer {
 public:
  EventBuffer() : events_(500), groups_(100) {}

  // Visible for testing
  EventBuffer(size_t event_capacity, size_t group_capacity)
      : events_(event_capacity), groups_(group_capacity) {}

  // Currently events are assumed to be added in timestamp order.
  // TODO: Manage timing inside the event buffer to ensure correct ordering.
  void Add(proto::Event& event);

  // Returns all the events timed between |from| and |to| (inclusive)
  std::vector<proto::Event> Get(int64_t from, int64_t to);

  // Returns all the event groups (events that share the same event_id)
  // that intersect the |from| and |to| range.
  // An event group spans from the first event with that event_id, to the
  // last event with event_id, or that end with an event of type |end|.
  std::vector<proto::EventGroup> Get(int64_t session_id,
                                     proto::Event::Kind kind,
                                     proto::Event::Type end, int64_t from,
                                     int64_t to);

  // Gets the group with the given event_id (in |group|), and returns true iff
  // it was found.
  bool GetGroup(int64_t event_id, proto::EventGroup* group);

 private:
  std::mutex mutex_;

  CircularBuffer<proto::Event> events_;

  CircularBuffer<proto::EventGroup> groups_;
};

}  // namespace profiler

#endif  // PERFD_EVENT_BUFFER_H_
