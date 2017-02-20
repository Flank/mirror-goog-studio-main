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
#include "background_queue.h"

#include <thread>

#include <gtest/gtest.h>
#include "utils/count_down_latch.h"

using profiler::BackgroundQueue;
using profiler::CountDownLatch;

TEST(BackgroundQueue, EnqueuingTasksWorks) {
  CountDownLatch job_1_waiting(1);
  CountDownLatch job_2_waiting(1);

  BackgroundQueue bq("BQTestThread");
  bq.EnqueueTask([&] { job_1_waiting.Await(); });
  bq.EnqueueTask([&] { job_2_waiting.Await(); });

  EXPECT_TRUE(bq.IsRunning());
  job_1_waiting.CountDown();

  EXPECT_TRUE(bq.IsRunning());
  job_2_waiting.CountDown();

  bq.Join();
  EXPECT_FALSE(bq.IsRunning());
}

TEST(BackgroundQueue, ResettingQueueKillsRemainingJobs) {
  CountDownLatch job_1_starting(1);
  CountDownLatch job_1_waiting(1);
  CountDownLatch job_2_waiting(1);

  BackgroundQueue bq("BQTestThread");
  bq.EnqueueTask([&] {
    job_1_starting.CountDown();
    job_1_waiting.Await();
  });
  bq.EnqueueTask([&] { job_2_waiting.Await(); });  // Will be reset before run

  job_1_starting.Await();
  EXPECT_TRUE(bq.IsRunning());
  bq.Reset();
  EXPECT_TRUE(bq.IsRunning());  // Job 1 is still running
  job_1_waiting.CountDown();

  // Job 2 is skipped, no need to decrement job_2_waiting latch
  bq.Join();
  EXPECT_FALSE(bq.IsRunning());

  bq.Reset();  // No harm resetting a finished Queue
  EXPECT_FALSE(bq.IsRunning());
}
