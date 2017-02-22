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
#include "count_down_latch.h"

#include <thread>

#include <gtest/gtest.h>

using profiler::CountDownLatch;
using std::thread;
using std::vector;

TEST(CountDownLatch, CountDownWorks) {
  CountDownLatch latch(3);
  EXPECT_EQ(3, latch.count());

  vector<thread> threads;

  for (int i = 0; i < 3; i++) {
    threads.push_back(thread([&latch]() {
      ASSERT_TRUE(latch.count() > 0);
      latch.CountDown();
    }));
  }

  latch.Await();
  EXPECT_EQ(0, latch.count());
  std::for_each(threads.begin(), threads.end(), [](thread &t) { t.join(); });

  // Awaits on an empty latch are a no-op and don't block
  latch.Await();

  // Extra count downs are also a no-op
  latch.CountDown();
  EXPECT_EQ(0, latch.count());
}
