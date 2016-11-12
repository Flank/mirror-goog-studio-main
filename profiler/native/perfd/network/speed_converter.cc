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
#include "speed_converter.h"

#include "utils/clock.h"

namespace profiler {

void SpeedConverter::Add(int64_t timestamp_us, int64_t bytes) {
  if (timestamp_us <= last_timestamp_us_ || bytes < last_bytes_) {
    return; // Ignore as this is invalid input and should never happen
  }

  int64_t delta_bytes = bytes - last_bytes_;
  Convert(last_timestamp_us_, timestamp_us, speed_, delta_bytes,
    &speed_, &speed_time_us_);

  last_timestamp_us_ = timestamp_us;
  last_bytes_ = bytes;
}

// As traffic data comes in, we want to create a report of rising and falling
// speeds. This class works by breaking each of these time slices up into
// triangle and trapezoid shapes. For example:
//
//      /|--
//     / |  \--
//    /  |     |-----|
//   /   |     |     |\
//  /    |     |     | \
// t₀    t₁    t₂    t₃    t₄
//
// where the height at each time represents a speed value that makes sense of
// the current bytes level (keeping in mind that the area under the curve
// represents bytes transferred).
void SpeedConverter::Convert(int64_t prev_time_us, int64_t curr_time_us,
  int64_t prev_speed, int64_t bytes, int64_t* speed, int64_t* speed_time_us) {

  // To visualize what's happening here:
  //
  // |\
  // |  \
  // |    \
  // |h₀   |h₁
  // |     |
  // t₀----t₁
  //
  // Since "A = 1/2(h₀ + h₁)*Δt" (where A is num bytes)
  // we can solve "h₁ = 2*bytes/Δt - h₀"
  int64_t delta_time_us = curr_time_us - prev_time_us;
  double prev_speed_us = (double)prev_speed / Clock::s_to_us(1);

  // bytes per µs
  double next_speed_us = (2.0 * bytes / delta_time_us - prev_speed_us);

  // bytes / µs * (µs / sec) = bytes / sec
  *speed = (int64_t)(next_speed_us * Clock::s_to_us(1));
  *speed_time_us = curr_time_us;

  if (*speed < 0) {
    // Special case - |bytes| is so small, that we need to drop our speed to 0
    // at some point *before* t₁. We can simplify this case to a triangle:
    //
    // |\
    // | \
    // h  \
    // |   \
    // |    \
    // t₀---t?-----t₁   // and from t? to t₁, speed is 0
    //
    // Since "A = 1/2(t?-t₀)h",
    // we can solve "t? = 2*A/h + t₀""
    *speed = 0;
    *speed_time_us = (int64_t)(2.0 * bytes / prev_speed_us + prev_time_us);
  }
}


}  // namespace profiler
