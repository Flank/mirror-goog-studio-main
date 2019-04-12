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
#include "clock.h"

#include <ctime>

namespace profiler {

int64_t SteadyClock::GetCurrentTime() const {
  timespec time;
  clock_gettime(CLOCK_MONOTONIC, &time);

  // time.tv_sec is of type 'time_t'; time.tv_nsec is of type 'long'.
  //
  // On a 32-bit device, they are both 4 bytes long. As a result, simplily
  // writing
  //     1000000000 * time.tv_sec + time.tv_nsec
  // in C++ would easily cause integer overflow because C++ would save the
  // multiplication result into a 32-bit integer. The overflow happens roughly
  // every 2.2 seconds.
  // Math: (2 ^ 31 -1) / 1000000000 ~= 2.15
  //
  // On a 64-bit device, time_t and long are both 8 bytes long.
  // Consider 'time.tv_sec' as in seconds held by a 64-bit integer. When the
  // unit changes to nanoseconds, the number becomes a billion times larger, and
  // the new value might be too large for a 64-bit integer to hold in principle.
  // However, in practice, Android devices reset CLOCK_MONOTONIC every time when
  // it reboots. In order to overflow, the device needs to be run 292 years.
  // Math: (2 ^ 63 - 1) / 1000000000 / 60 / 60 / 24 / 365 ~= 292.47
  //
  // Therefore, it's reasonable for our purpose to use int64_t to hold the
  // timestamp in nanosecond unit. Clock::s_to_ns() has the right input and
  // output types, and it works on both 32-bit and 64-bit platforms. Its
  // signature is as follows. Note constexpr implies inline.
  //   static constexpr int64_t s_to_ns(int64_t s);
  return Clock::s_to_ns(time.tv_sec) + time.tv_nsec;
}

}  // namespace profiler
