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
#include "utils/shared_mutex.h"

#include <gtest/gtest.h>
#include <atomic>
#include <mutex>
#include <thread>
#include <vector>

using profiler::shared_lock;
using profiler::shared_mutex;

// Since our implementation of shared_mutex is just a thin wrapper
// over pthread_rwlock_t, this test doesn't perform extensive testing.
// The goal here is to make sure that right pthreads functions were
// called where they are needed.
TEST(SharedMutex, SanityCheck) {
  shared_mutex m;
  const int thread_count = 10;
  std::atomic<int> threads_started(0);
  std::atomic<int> locks_acquired(0);
  std::vector<std::thread> threads;

  {
    // Take a write lock and start several threads waiting to
    // take a read lock.
    std::lock_guard<shared_mutex> write_lock(m);
    for (int i = 0; i < thread_count; i++) {
      threads.emplace_back([&] {
        threads_started++;
        shared_lock<shared_mutex> read_lock(m);
        locks_acquired++;
        while (locks_acquired != thread_count) {
          std::this_thread::yield();
        }
      });
    }

    // Wait till all threads started.
    while (threads_started != thread_count) {
      std::this_thread::yield();
    }
    assert(locks_acquired == 0);

    // At this poin all threads are started and waiting on read_lock
    // until write_lock releases the mutex.
  }

  // Wait till all threads finish their work
  while (locks_acquired != thread_count) {
    std::this_thread::yield();
  }

  std::lock_guard<shared_mutex> write_lock(m);

  // All threads finished and released read_lock
  // otherwise this thread wouldn't be able to take a write lock.
  for (int i = 0; i < thread_count; i++) threads[i].join();
}
