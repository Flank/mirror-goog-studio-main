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
#ifndef TIME_VALUE_BUFFER_H_
#define TIME_VALUE_BUFFER_H_

#include <time.h>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

namespace profiler {

// Data per sample. The time field indicates a independent time point when
// value is collected.
template <typename T>
struct TimeValue {
  int64_t time;
  T value;
};

// Data holder class of time sequential collected information. For example,
// traffic bytes sent and received information are repeated collected. It stores
// data and provides query functionality.
// TODO: Refactor name to ProfilerBuffer for profiler sampling data.
template <typename T>
class TimeValueBuffer {
 public:
  TimeValueBuffer(size_t capacity, int pid = -1)
      : capacity_(capacity), pid_(pid), values_(new TimeValue<T>[capacity_]) {}

  // Add sample value collected at a given time point.
  // TODO: We are moving to int64_t from timespec, this method is not needed.
  void Add(T value, const timespec &sample_time) {
    Add(value, 1e9 * sample_time.tv_sec + sample_time.tv_nsec);
  }

  void Add(T value, const int64_t sample_time) {
    std::lock_guard<std::mutex> lock(values_mutex_);
    size_t index = size_ < capacity_ ? size_ : start_;
    values_[index].time = sample_time;
    values_[index].value = value;
    if (size_ < capacity_) {
      size_++;
    } else {
      start_ = (start_ + 1) % capacity_;
    }
  }

  // Returns data within the given range [time_from, time_to).
  // TODO: We are moving to int64_t from timespec, this method is not needed.
  std::vector<TimeValue<T>> Get(const timespec &time_from,
                                const timespec &time_to) {
    int64_t from = 1e9 * time_from.tv_sec + time_from.tv_nsec;
    int64_t to = 1e9 * time_to.tv_sec + time_to.tv_nsec;
    std::lock_guard<std::mutex> lock(values_mutex_);
    std::vector<TimeValue<T>> result;
    for (size_t i = 0; i < size_; i++) {
      size_t index = (start_ + i) % capacity_;
      if (values_[index].time >= from && values_[index].time < to) {
        result.push_back(values_[index]);
      }
    }
    return result;
  }

  std::vector<T> GetValues(const int64_t time_from, const int64_t time_to) {
    std::lock_guard<std::mutex> lock(values_mutex_);
    std::vector<T> result;
    for (size_t i = 0; i < size_; i++) {
      size_t index = (start_ + i) % capacity_;
      if (values_[index].time >= time_from && values_[index].time < time_to) {
        result.push_back(values_[index].value);
      }
    }
    return result;
  }

  // Returns the number of samples stored.
  size_t GetSize() {
    std::lock_guard<std::mutex> lock(values_mutex_);
    return size_;
  }

  // Returns collected sample data at given index.
  TimeValue<T> Get(size_t index) {
    std::lock_guard<std::mutex> lock(values_mutex_);
    TimeValue<T> result = values_[(start_ + index) % capacity_];
    return result;
  }

  // Returns app id that the profiler data buffer is for.
  int pid() { return pid_; }

 private:
  // Indicates the maximum number of samples it can hold.
  const size_t capacity_;
  const int pid_;

  // TODO: Temporarily uses dynamic array. It should change to circular buffer.
  std::unique_ptr<TimeValue<T>[]> values_;
  std::mutex values_mutex_;
  size_t size_ = 0;
  size_t start_ = 0;
};

}  // namespace profiler

#endif  // TIME_VALUE_BUFFER_H_
