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
#ifndef UTILS_CHANNEL_H
#define UTILS_CHANNEL_H

#include <atomic>
#include <cassert>
#include <queue>

#include "utils/count_down_latch.h"

namespace profiler {

// A channel provides a safe way for communicating data values across a thread
// boundary. The consumer-side will block waiting for data added by a producer.
//
// Example:
//   Channel<int32_t> c;
//
//   In thread #1
//   ============
//   int val;
//   // |Pop| will block until a value is available or the channel is finished
//   while (c.Pop(&val)) {
//     ...
//   }
//
//   In thread #2
//   ============
//   c.Push(long_operation_1());
//   c.Push(long_operation_2());
//   c.Push(long_operation_3());
//   c.Push(long_operation_4());
//   c.Finish();
template <typename T>
class Channel {
 public:
  Channel() : is_finished(false) {}

  // Push a value into the channel. Values will be consumed in the order entered
  // by calls to |Pop|. If |Finish| was called on this channel, then the value
  // entered here will be ignored (and |false| will be returned to indicate it).
  bool Push(T value) {
    if (is_finished) {
      return false;
    }

    std::lock_guard<std::mutex> lock(queue_mutex_);
    inner_queue_.push(value);
    allow_pop_.notify_one();
    return true;
  }

  // Pull a value out of the channel added by |Push|. If the channel is
  // currently empty, this call will block until a value is put in, unless
  // the channel was marked finished by calling |Finish|, at which point it will
  // exit immediately and return |false|.
  bool Pop(T* value) {
    std::unique_lock<std::mutex> lock(queue_mutex_);
    allow_pop_.wait(lock,
                    [&]() { return is_finished || !inner_queue_.empty(); });

    if (!inner_queue_.empty()) {
      *value = inner_queue_.front();
      inner_queue_.pop();
      return true;
    } else {
      return false;
    }
  }

  // Indicate that this channel shouldn't accept values anymore. When calling
  // |Pop| on an empty channel that is finished, instead of blocking
  // indefinitely, the method will return |false| immediately. This allows
  // callers to pull data out of a channel in a while loop which will break
  // automatically when the channel is finished.
  void Finish() {
    is_finished = true;
    allow_pop_.notify_all();
  }

  // How many values remain in this queue
  size_t length() const {
    std::lock_guard<std::mutex> lock(queue_mutex_);
    return inner_queue_.size();
  }

 private:
  std::atomic_bool is_finished;
  std::condition_variable allow_pop_;
  mutable std::mutex queue_mutex_;
  std::queue<T> inner_queue_;
};

}  // namespace profiler

#endif  // UTILS_CHANNEL_H
