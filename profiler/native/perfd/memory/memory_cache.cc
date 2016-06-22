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
#include "memory_cache.h"

using ::profiler::proto::MemoryData;

namespace profiler {

MemoryCache::MemoryCache(const Clock& clock, int32_t samples_capacity) :
    memory_samples_(new MemoryData::MemorySample[samples_capacity]), clock_(clock),
    put_memory_sample_index_(0), samples_capacity_(samples_capacity), buffer_full_(false) {
}

void MemoryCache::SaveMemorySample(const MemoryData::MemorySample& sample) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);

  memory_samples_[put_memory_sample_index_].CopyFrom(sample);
  memory_samples_[put_memory_sample_index_].set_timestamp(clock_.GetCurrentTime());
  put_memory_sample_index_ = IncrementSampleIndex(put_memory_sample_index_);
  if (put_memory_sample_index_ == 0) buffer_full_ = true; // Check if we have wrapped.
}

void MemoryCache::SaveInstanceCountSample(const MemoryData::InstanceCountSample& sample) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);
}

void MemoryCache::SaveGcSample(const MemoryData::GcSample& sample) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);
}

void MemoryCache::LoadMemoryData(
    int64_t start_time_exl, int64_t end_time_inc, MemoryData* response) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);

  int32_t i;
  if (buffer_full_) {
    i = put_memory_sample_index_;
  }
  else if (put_memory_sample_index_ == 0) {
    return; // Cache is empty.
  }
  else {
    i = 0;
  }

  int64_t end_timestamp = -1;
  do {
    int64_t timestamp = memory_samples_[i].timestamp();
    // TODO add optimization to skip past already-queried entries if the array gets large.
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_mem_samples()->CopyFrom(memory_samples_[i]);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
    i = IncrementSampleIndex(i);
  } while (i != put_memory_sample_index_);

  response->set_end_timestamp(end_timestamp);
}

int32_t MemoryCache::IncrementSampleIndex(int32_t index) {
  return (index + 1) % samples_capacity_;
}

} // namespace profiler
