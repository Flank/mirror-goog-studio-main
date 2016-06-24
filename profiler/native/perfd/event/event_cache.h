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

#include <grpc++/grpc++.h>

#include "proto/internal_event.grpc.pb.h"

namespace profiler {

class EventCache {
 public:
  // Adds data to the event cache, the data is copied.
  void AddData(const profiler::proto::EventProfilerData& data);

  // Populates a EventDataResponse with a copy of the EventProfilerData protos
  // that
  // exist within a given time range. The start time is exclusive, while the end
  // time is inclusive.
  void GetData(int64_t start_time, int64_t end_time,
               profiler::proto::EventDataResponse* response);

 private:
  // TODO: The current cache grows unlimited, the data needs a timeout, or
  // changed to a ring buffer.
  std::vector<profiler::proto::EventProfilerData> cache_;
  // Guards |cache_|
  std::mutex cache_mutex_;
};

}  // end of namespace profiler

#endif  // PERFD_EVENT_EVENT_CACHE_H_
