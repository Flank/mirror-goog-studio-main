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
#ifndef UTILS_SHARED_MUTEX_H_
#define UTILS_SHARED_MUTEX_H_

#include <assert.h>
#include <pthread.h>

namespace profiler {

// This is a wrapper around R/W lock from pthreads that
// has the same interface as std::shared_mutex from C++17.
// TODO (b/76101346): Delete this class once NDK starts supporting C++17 STL.
class shared_mutex {
 public:
  shared_mutex() {
    int result = pthread_rwlock_init(&rwlock_, nullptr);
    assert(result == 0);
  }
  ~shared_mutex() {
    int result = pthread_rwlock_destroy(&rwlock_);
    assert(result == 0);
  }

  shared_mutex(const shared_mutex&) = delete;
  shared_mutex& operator=(const shared_mutex&) = delete;

  void lock() {
    int result = pthread_rwlock_wrlock(&rwlock_);
    assert(result == 0);
  }

  void unlock() {
    int result = pthread_rwlock_unlock(&rwlock_);
    assert(result == 0);
  }

  void lock_shared() {
    int result = pthread_rwlock_rdlock(&rwlock_);
    assert(result == 0);
  }
  void unlock_shared() {
    int result = pthread_rwlock_unlock(&rwlock_);
    assert(result == 0);
  }

 private:
  pthread_rwlock_t rwlock_;
};

template <class Mutex>
class shared_lock {
 public:
  // Shared locking
  explicit shared_lock(Mutex& m) : mutex_(m) { mutex_.lock_shared(); }
  ~shared_lock() { mutex_.unlock_shared(); }

  shared_lock(const shared_lock&) = delete;
  shared_lock& operator=(const shared_lock&) = delete;

 private:
  Mutex& mutex_;
};

}  // namespace profiler

#endif  // UTILS_SHARED_MUTEX_H_