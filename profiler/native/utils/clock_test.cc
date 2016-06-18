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

#include <gtest/gtest.h>

using profiler::Clock;

TEST(Clock, SanityCheckTimeConversionMethods) {
  int64_t ns = 1000000000;
  int64_t us = 1000000;
  int64_t ms = 1000;
  int64_t s = 1;

  EXPECT_EQ(Clock::ns_to_us(ns), us);
  EXPECT_EQ(Clock::ns_to_us(ns), us);
  EXPECT_EQ(Clock::ns_to_ms(ns), ms);
  EXPECT_EQ(Clock::ns_to_s(ns), s);
  EXPECT_EQ(Clock::us_to_ns(us), ns);
  EXPECT_EQ(Clock::us_to_ms(us), ms);
  EXPECT_EQ(Clock::us_to_s(us), s);
  EXPECT_EQ(Clock::ms_to_ns(ms), ns);
  EXPECT_EQ(Clock::ms_to_us(ms), us);
  EXPECT_EQ(Clock::ms_to_s(ms), s);
  EXPECT_EQ(Clock::s_to_ns(s), ns);
  EXPECT_EQ(Clock::s_to_us(s), us);
  EXPECT_EQ(Clock::s_to_ms(s), ms);
}
