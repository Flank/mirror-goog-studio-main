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
#include "channel.h"

#include <thread>
#include <vector>

#include <gtest/gtest.h>

#include "utils/count_down_latch.h"

using profiler::Channel;
using profiler::CountDownLatch;
using std::thread;
using std::vector;

TEST(Channel, CommunicatesAcrossThreads) {
  Channel<int32_t> c;
  auto producer = thread([&]() {
    c.Push(1);
    c.Push(2);
    c.Push(3);
    c.Push(4);
    c.Finish();
  });

  auto consumer = thread([&]() {
    int val;
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 1);
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 2);
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 3);
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 4);
    EXPECT_FALSE(c.Pop(&val));
  });

  producer.join();
  consumer.join();

  EXPECT_EQ(0, c.length());
}

TEST(Channel, CanConsumeFromMultipleThreads) {
  Channel<int32_t> c;
  auto producer = thread([&]() {
    c.Push(1);
    c.Push(2);
    c.Push(3);
    c.Push(4);
    c.Push(5);
    c.Push(6);
  });

  vector<thread> threads;
  for (int i = 0; i < 3; i++) {
    threads.push_back(thread([&]() {
      int val;
      EXPECT_TRUE(c.Pop(&val));
      EXPECT_TRUE(c.Pop(&val));
    }));
  }

  std::for_each(threads.begin(), threads.end(), [](thread &t) { t.join(); });
  producer.join();

  EXPECT_EQ(0, c.length());
}

TEST(Channel, CanProduceFromMultipleThreads) {
  Channel<int32_t> c;
  CountDownLatch done_producing_(3);

  auto consumer = thread([&]() {
    int val;
    EXPECT_TRUE(c.Pop(&val));  // 1
    EXPECT_TRUE(c.Pop(&val));  // 2
    EXPECT_TRUE(c.Pop(&val));  // 3
    EXPECT_TRUE(c.Pop(&val));  // 4
    EXPECT_TRUE(c.Pop(&val));  // 5
    EXPECT_TRUE(c.Pop(&val));  // 6
    EXPECT_FALSE(c.Pop(&val));
  });

  vector<thread> threads;
  for (int i = 0; i < 3; i++) {
    threads.push_back(thread([&]() {
      int thread_index = (2 * i) + 1;  // (1 and 2) or (3 and 4) or (5 and 6)
      c.Push(thread_index);
      c.Push(thread_index + 1);
      done_producing_.CountDown();
    }));
  }
  threads.push_back(thread([&]() {
    done_producing_.Await();
    c.Finish();
  }));

  std::for_each(threads.begin(), threads.end(), [](thread &t) { t.join(); });
  consumer.join();

  EXPECT_EQ(0, c.length());
}

TEST(Channel, ValuesAfterFinishAreIgnored) {
  Channel<int32_t> c;
  auto producer = thread([&]() {
    EXPECT_TRUE(c.Push(1));
    EXPECT_TRUE(c.Push(2));
    EXPECT_TRUE(c.Push(3));
    c.Finish();
    EXPECT_FALSE(c.Push(4));
    EXPECT_FALSE(c.Push(5));
    EXPECT_FALSE(c.Push(6));
  });

  auto consumer = thread([&]() {
    int val;
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 1);
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 2);
    EXPECT_TRUE(c.Pop(&val));
    EXPECT_EQ(val, 3);
    EXPECT_FALSE(c.Pop(&val));
  });

  producer.join();
  consumer.join();

  EXPECT_EQ(0, c.length());
}
