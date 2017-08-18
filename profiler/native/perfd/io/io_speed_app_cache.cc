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
#include "io_speed_app_cache.h"
#include "utils/clock.h"

using std::lock_guard;
using std::mutex;
using std::vector;

namespace profiler {
// capacity of 2000 means it'll keep up to 1000 call for read and write each
IoSpeedAppCache::IoSpeedAppCache(int32_t app_id)
    : capacity_(2000),
      app_id_(app_id),
      sampling_interval_(Clock::ms_to_ns(100)) {}

void IoSpeedAppCache::AddIoCall(int64_t start_timestamp, int64_t end_timestamp,
                                int32_t bytes_count,
                                profiler::proto::IoType type) {
  // shouldn't happen practically but just in case.
  if (start_timestamp == end_timestamp) {
    end_timestamp++;
  }
  lock_guard<mutex> lock(
      (type == profiler::proto::READ ? read_speed_mutex_ : write_speed_mutex_));

  auto &speed_points = (type == profiler::proto::READ ? read_speed_points_
                                                      : write_speed_points_);
  // After this the size will be at most |capacity_| - 2, as we'll insert two
  // points
  while (speed_points.size() >= capacity_ - 1) {
    // Remove the first point i.e. the point with the oldest timestamp and
    // add it's speed to the next point so the cumlative speed will remain the
    // same
    auto oldest = speed_points.begin();
    int64_t speed = oldest->speed;
    speed_points.erase(oldest);
    speed_points.begin()->speed = speed_points.begin()->speed + speed;
  }

  int64_t speed = bytes_count * static_cast<int64_t>(1e9) /
                  (end_timestamp - start_timestamp);
  SpeedPoint first_point;
  first_point.timestamp = start_timestamp;
  first_point.speed = speed;

  SpeedPoint second_point;
  second_point.timestamp = end_timestamp;
  second_point.speed = -speed;

  speed_points.insert(first_point);
  speed_points.insert(second_point);
}

// Given a range t0 and t1 and I/O calls a-f...
//
//               t0                t1
// a: [===========|=================|=========]
// b: [=======]   |                 |
// c:         [===|====]            |
// d:             |  [==========]   |
// e:             |           [=====|===]
// f:             |                 |   [=======]
//                x  x  x  x  x  x  x
//
// The function will divide the query interval to smaller intervals of length
// |sampling_interval_|, and for each small interval report the average speed
// amongst the I/O calls happened in it

vector<SpeedDetails> IoSpeedAppCache::GetSpeedData(
    int64_t start_timestamp, int64_t end_timestamp,
    profiler::proto::IoType type) const {
  lock_guard<mutex> lock(
      (type == profiler::proto::READ ? read_speed_mutex_ : write_speed_mutex_));
  vector<SpeedDetails> speed_data;

  int64_t current_speed = 0;
  int64_t sampled_speed = 0;
  int64_t last_timestamp = start_timestamp;
  int64_t previous_timestamp = start_timestamp;

  auto get_speed_details = [](int64_t timestamp, int64_t speed) {
    SpeedDetails new_speed_details;
    new_speed_details.timestamp = timestamp;
    new_speed_details.speed = speed;
    return new_speed_details;
  };

  for (auto speed_point :
       (type == profiler::proto::READ ? read_speed_points_
                                      : write_speed_points_)) {
    // Handle the case of the first query by the poller, add a zero speed point
    // just before the very first I/O call
    if (last_timestamp == std::numeric_limits<int64_t>::min()) {
      last_timestamp = speed_point.timestamp - 1;
      previous_timestamp = speed_point.timestamp - 1;
      speed_data.push_back(get_speed_details(last_timestamp, 0));
    }
    while (std::min(speed_point.timestamp, end_timestamp) >
           last_timestamp + sampling_interval_) {
      sampled_speed +=
          current_speed *
          (last_timestamp + sampling_interval_ - previous_timestamp) /
          sampling_interval_;
      speed_data.push_back(get_speed_details(
          last_timestamp + (sampling_interval_ / 2), sampled_speed));
      sampled_speed = 0;
      last_timestamp += sampling_interval_;
      previous_timestamp = last_timestamp;
    }
    if (speed_point.timestamp > end_timestamp) {
      sampled_speed += current_speed * (end_timestamp - previous_timestamp) /
                       sampling_interval_;
      speed_data.push_back(get_speed_details(end_timestamp, sampled_speed));
      last_timestamp = end_timestamp;
      previous_timestamp = end_timestamp;
      break;
    }

    if (speed_point.timestamp > previous_timestamp) {
      sampled_speed += current_speed *
                       (speed_point.timestamp - previous_timestamp) /
                       sampling_interval_;
      previous_timestamp = speed_point.timestamp;
    }

    current_speed += speed_point.speed;
  }

  if (last_timestamp != previous_timestamp) {
    speed_data.push_back(get_speed_details(previous_timestamp, sampled_speed));
  }

  // Handle the case when there's no I/O call in the query interval. The +1 is
  // because the start timestamp is exclusive
  if (speed_data.empty() &&
      start_timestamp != std::numeric_limits<int64_t>::min()) {
    speed_data.push_back(get_speed_details(start_timestamp + 1, 0));
  }
  return speed_data;
}

}  // namespace profiler
