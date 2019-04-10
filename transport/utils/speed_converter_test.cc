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
#include "utils/speed_converter.h"

#include <gtest/gtest.h>

#include "utils/clock.h"

using profiler::Clock;
using profiler::SpeedConverter;

TEST(SpeedConverter, NoDataAddedReturnsNoSpeed) {
  SpeedConverter converter(12345, 1000);

  EXPECT_EQ(0, converter.speed());
  EXPECT_EQ(12345, converter.speed_time_ns());
}

TEST(SpeedConverter, OneAddProducesExpectedSpeed) {
  SpeedConverter converter(0, 0);

  // A final speed of 2K / sec, starting from 0K / sec, will allow us to
  // download 1K of bytes after 1 sec
  converter.Add(Clock::s_to_ns(1), 1024);
  EXPECT_EQ(2048, converter.speed());
  EXPECT_EQ(Clock::s_to_ns(1), converter.speed_time_ns());
}

TEST(SpeedConverter, TwoAddsProducesExpectedSpeed) {
  SpeedConverter converter(0, 0);
  converter.Add(Clock::s_to_ns(1), 1024);         // Final speek, 2K / sec
  converter.Add(Clock::s_to_ns(2), 1024 + 2048);  // Maintain 2K / sec

  EXPECT_EQ(2048, converter.speed());
  EXPECT_EQ(Clock::s_to_ns(2), converter.speed_time_ns());
}

TEST(SpeedConverter, SpeedCanDropToZero) {
  SpeedConverter converter(0, 0);
  converter.Add(Clock::s_to_ns(1), 1024);
  // Only 100 bytes left to transfer, so speed will drop to 0
  converter.Add(Clock::s_to_ns(2), 1024 + 400);

  EXPECT_EQ(0, converter.speed());
  EXPECT_LT(Clock::s_to_ns(1), converter.speed_time_ns());
  EXPECT_GT(Clock::s_to_ns(2), converter.speed_time_ns());

  // Speed stays at 0...
  converter.Add(Clock::s_to_ns(3), 1024 + 400);
  EXPECT_EQ(0, converter.speed());
  EXPECT_EQ(Clock::s_to_ns(3), converter.speed_time_ns());
}
