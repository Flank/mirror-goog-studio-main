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
#ifndef MEMORY_CACHE_H_
#define MEMORY_CACHE_H_

#include <cstdint>
#include <mutex>

#include "proto/memory.pb.h"
#include "utils/clock.h"

namespace profiler {

// Class to provide memory data saving interface, this is an empty definition.
class MemoryCache {
public:
  MemoryCache(int32_t samples_count);
  ~MemoryCache();

  virtual void SaveMemorySample(const proto::MemoryData::MemorySample& sample);
  virtual void SaveInstanceCountSample(const proto::MemoryData::InstanceCountSample& sample);
  virtual void SaveGcSample(const proto::MemoryData::GcSample& sample);

  virtual void LoadMemorySamples(int64_t start_time_exl, int64_t end_time_inc,
      proto::MemoryData* response);
  virtual void LoadInstanceCountSamples(int64_t start_time_exl, int64_t end_time_inc,
      proto::MemoryData* response);
  virtual void LoadGcSamples(int64_t start_time_exl, int64_t end_time_inc,
      proto::MemoryData* response);

private:
  proto::MemoryData_MemorySample* memory_samples_;
  std::mutex memory_samples_mutex_;
  int32_t put_memory_sample_index_;
  int32_t samples_capacity_;
  bool buffer_full_;

  int32_t IncrementSampleIndex(int32_t index);
};

} // namespace profiler

#endif // MEMORY_CACHE_H_
