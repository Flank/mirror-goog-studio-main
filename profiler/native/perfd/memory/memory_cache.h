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
#include <memory>
#include <mutex>
#include <string>

#include "proto/memory.pb.h"
#include "utils/clock.h"

namespace profiler {

// Class to provide memory data saving interface, this is an empty definition.
class MemoryCache {
 public:
  explicit MemoryCache(const Clock& clock, int32_t samples_capacity);

  void SaveMemorySample(const proto::MemoryData::MemorySample& sample);
  void SaveVmStatsSample(const proto::MemoryData::VmStatsSample& sample);
  bool StartHeapDumpSample(const proto::MemoryData::HeapDumpSample& sample);
  bool EndHeapDumpSample(int64_t end_time, bool success);

  void LoadMemoryData(int64_t start_time_exl, int64_t end_time_inc,
                      proto::MemoryData* response);

 private:
  const Clock& clock_;

  // TODO replace these with circular buffer class when it becomes available.
  std::unique_ptr<proto::MemoryData::MemorySample[]> memory_samples_;
  std::unique_ptr<proto::MemoryData::VmStatsSample[]> vm_stats_samples_;
  std::unique_ptr<proto::MemoryData::HeapDumpSample[]> heap_dump_samples_;
  std::mutex memory_samples_mutex_;
  std::mutex vm_stats_samples_mutex_;
  std::mutex heap_dump_samples_mutex_;

  int32_t put_memory_sample_index_;
  int32_t put_vm_stats_sample_index_;
  int32_t put_heap_dump_sample_index_;
  int32_t samples_capacity_;

  bool memory_samples_buffer_full_;
  bool vm_stats_samples_buffer_full_;
  bool heap_dump_samples_buffer_full_;
  bool has_unfinished_heap_dump_sample_;

  int32_t IncrementSampleIndex(int32_t index);
};

}  // namespace profiler

#endif  // MEMORY_CACHE_H_
