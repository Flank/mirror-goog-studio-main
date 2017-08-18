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
#ifndef PERFD_IO_IO_SPEED_APP_CACHE_H_
#define PERFD_IO_IO_SPEED_APP_CACHE_H_

#include <mutex>
#include <set>
#include <unordered_map>

#include "perfd/io/speed_details.h"
#include "proto/io.grpc.pb.h"

namespace profiler {

class IoSpeedAppCache final {
 public:
  explicit IoSpeedAppCache(int32_t app_id);

  // Adds the I/O call to the cache
  void AddIoCall(int64_t start_timestamp, int64_t end_timestamp,
                 int32_t bytes_count, profiler::proto::IoType type);
  // Returns speed data for the given and interval
  std::vector<SpeedDetails> GetSpeedData(int64_t start_timestamp,
                                         int64_t end_timestamp,
                                         profiler::proto::IoType type) const;
  // Returns the app id whose data is being saved in this cache object.
  int32_t app_id() const { return app_id_; }

 private:
  struct SpeedPoint final {
    // The timestamp the speed info represents.
    int64_t timestamp;
    // The speed of reading or writing at the specified timestamp.
    mutable int64_t speed;

    bool operator<(const SpeedPoint& s) const {
      return timestamp < s.timestamp;
    }
  };
  // The maximum capacity of the speed_points_ set.
  const int32_t capacity_;
  const int32_t app_id_;
  // The interval of speed sampling
  const int64_t sampling_interval_;
  // Mutex guards read_speed_points_
  mutable std::mutex read_speed_mutex_;
  // Mutex guards write_speed_points_
  mutable std::mutex write_speed_mutex_;
  // Contains each I/O call start and end points, should be used to get
  // cumulative speed at each point.
  std::multiset<SpeedPoint> read_speed_points_;
  std::multiset<SpeedPoint> write_speed_points_;
};

}  // namespace profiler

#endif  // PERFD_IO_IO_SPEED_APP_CACHE_H_
