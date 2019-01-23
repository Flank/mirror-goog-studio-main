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
#ifndef TRANSPORT_EVENT_BUFFER_H_
#define TRANSPORT_EVENT_BUFFER_H_

#include <condition_variable>
#include <mutex>
#include <queue>
#include "proto/profiler.grpc.pb.h"
#include "utils/circular_buffer.h"
#include "utils/clock.h"

namespace profiler {

class EventWriter;

// This class is thread safe
class EventBuffer {
 public:
  EventBuffer(Clock* clock)
      : clock_(clock),
        interrupt_write_(false),
        events_added_(0),
        events_(500),
        groups_(100) {}

  // Visible for testing
  EventBuffer(Clock* clock, size_t events_capacity, size_t group_capacity)
      : clock_(clock),
        interrupt_write_(false),
        events_added_(0),
        events_(events_capacity),
        groups_(group_capacity) {}

  // Note that the |EventBuffer| will generate/overwrite the event's timestamp
  // based on the time it is added. This ensure that the events stored in the
  // buffer is in order.
  void Add(proto::Event& event);

  // All current and new events are written to the |writer|.
  // This call is blocking and will not return until InterruptWriteEvents is
  // called.
  void WriteEventsTo(EventWriter* writer);

  // Interrupts the WriteEventsTo.
  void InterruptWriteEvents();

  // Returns all the event groups (events that share the same group_id)
  // that intersect the |from| and |to| range.
  // An event group spans from the first event with that group_id, to the
  // last event with group_id, or that end with an event of type |end|.
  std::vector<proto::EventGroup> Get(proto::Event::Kind kind, int64_t from,
                                     int64_t to);

  // Gets the group with the given group_id (in |group|), and returns true iff
  // it was found.
  bool GetGroup(int64_t group_id, proto::EventGroup* group);

 private:
  Clock* clock_;
  std::mutex mutex_;
  bool interrupt_write_;
  // Guarded by mutex_
  int events_added_;
  std::condition_variable events_cv_;
  // Guarded by mutex_
  CircularBuffer<proto::Event> events_;
  // Guarded by mutex_
  CircularBuffer<proto::EventGroup> groups_;
};

}  // namespace profiler

#endif  // TRANSPORT_EVENT_BUFFER_H_
