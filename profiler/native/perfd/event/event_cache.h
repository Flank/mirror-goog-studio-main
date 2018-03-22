/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef PERFD_EVENT_EVENT_CACHE_H_
#define PERFD_EVENT_EVENT_CACHE_H_

#include <memory>
#include <mutex>
#include <thread>
#include <unordered_map>

#include <grpc++/grpc++.h>

#include "proto/internal_event.grpc.pb.h"
#include "utils/clock.h"

namespace profiler {

class EventCache {
 public:
  explicit EventCache(Clock* clock) : clock_(clock) {}
  // Adds data to the event cache, the data is copied.
  void AddActivityData(const proto::ActivityData& data);
  void AddSystemData(const proto::SystemData& data);

  // Populates a Response with a copy of the proper protos that exist within a
  // given time range. The start time is exclusive, while the end time is
  // inclusive.
  void GetActivityData(int32_t app_id, int64_t start_time, int64_t end_time,
                       proto::ActivityDataResponse* response);

  void GetSystemData(int32_t app_id, int64_t start_time, int64_t end_time,
                     proto::SystemDataResponse* response);

  void MarkActivitiesAsTerminated(int32_t pid);

 private:
  // Per-app storage of system and activity data.
  struct CacheMaps {
    // TODO: The current cache grows unlimited, the data needs a timeout, or
    // changed to a ring buffer.
    // Map of event id to SystemData to map start/stop events
    std::map<int64_t, proto::SystemData> system_cache;
    // Map of activity hash to activity data to map activity states.
    std::map<int32_t, proto::ActivityData> activity_cache;
  };

  // Guards caches
  std::mutex cache_mutex_;
  // App's Id to CacheMaps map.
  std::unordered_map<int32_t, CacheMaps> cache_;

  Clock* clock_;
};

}  // end of namespace profiler

#endif  // PERFD_EVENT_EVENT_CACHE_H_
