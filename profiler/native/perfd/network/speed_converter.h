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
#ifndef PERFD_NETWORK_SPEED_CONVERTER_H_
#define PERFD_NETWORK_SPEED_CONVERTER_H_

#include <cstdint>

namespace profiler {

// Class which converts input of "bytes so far" into a list of speeds (B/s). To
// use the class, add sequential timestamp:byte pairs, and then query for the
// most recent timestamp:speed values.
//
// Android devices return absolute number of bytes sent / received since device
// boot; however, we're more interested in current speeds, so this class handles
// transforming the data appropriately.
class SpeedConverter final {
 public:
  // Initialize this converter with the current state of the device. |bytes|
  // should represent the number of bytes transferred since device boot.
  SpeedConverter(int64_t timestamp_ns, int64_t bytes)
      : last_timestamp_ns_(timestamp_ns),
        last_bytes_(bytes),
        speed_time_ns_(timestamp_ns) {}

  // Add the next data point of |bytes| transferred since device boot, and from
  // that, we'll calculate the latest speed. |timestamp_ns| should always be a
  // larger value than before, and |bytes| should stay the same or increase over
  // time. Other values are ignored as invalid.
  void Add(int64_t timestamp_ns, int64_t bytes);

  // Return the last speed calculated (in bytes per second) after the last call
  // to |Add|
  int64_t speed() { return speed_; }

  // Return the last timestamp calculated after the last call to |Add|. This
  // will always equal the timestamp passed into |Add| unless the speed dropped
  // to 0 since the previous call to |Add|. See the class header comment for
  // more details.
  int64_t speed_time_ns() { return speed_time_ns_; }

 private:
  // Given the last speed value and other relevant values, calculate the next
  // speed value we need to generate a timeslice that would produce |bytes|.
  // Calculated values will be output into |speed| and |speed_time_ns|.
  //
  // Note that |next_time_ns| will usually be the same as |curr_time_ns|, unless
  // the speed dropped to 0 at some point between |prev_time_ns| and
  // |curr_time_ns|.
  static void Convert(int64_t prev_time_ns, int64_t curr_time_ns,
                      int64_t prev_speed, int64_t bytes, int64_t* speed,
                      int64_t* speed_time_ns);

  int64_t last_timestamp_ns_;
  int64_t last_bytes_;

  int64_t speed_time_ns_;
  int64_t speed_{0};
};

}  // namespace profiler

#endif  // PERFD_NETWORK_SPEED_CONVERTER_H_
