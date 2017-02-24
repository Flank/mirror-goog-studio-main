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
#include "utils/file_cache.h"

namespace profiler {

// Class to provide memory data saving interface, this is an empty definition.
class MemoryCache {
 public:
  // Indicates that a heap dump is in progress.
  static const int64_t kUnfinishedTimestamp = -1;

  explicit MemoryCache(const Clock& clock, FileCache& file_cache,
                       int32_t samples_capacity);

  void SaveMemorySample(const proto::MemoryData::MemorySample& sample);
  void SaveVmStatsSample(const proto::MemoryData::VmStatsSample& sample);
  // Saves a new HeapDumpInfo sample based on the dump_file_path and
  // request_time parameters. This method returns false if a heap dump
  // is still in progress (e.g. a matching EndHeapDump has not been
  // called from a previous StartHeapDump). Otherwise this method returns
  // true indicating a HeapDumpInfo has been added. On return, the response
  // parameter is populated with the most recent HeapDumpInfo.
  bool StartHeapDump(const std::string& dump_file_path,
                     int64_t request_time,
                     proto::TriggerHeapDumpResponse* response);
  bool EndHeapDump(int64_t end_time, bool success);
  void TrackAllocations(bool enabled, bool legacy,
                        proto::TrackAllocationsResponse* response);

  void LoadMemoryData(int64_t start_time_exl, int64_t end_time_inc,
                      proto::MemoryData* response);
  void ReadHeapDumpFileContents(int64_t dump_time,
                                proto::DumpDataResponse* response);

 private:
  // Gets the index into |*_samples_| for the corresponding |id|.
  int32_t GetSampleIndex(int32_t id);
  // Gets the next index into |*_samples_| for the corresponding |id|.
  int32_t GetNextSampleIndex(int32_t id);

  const Clock& clock_;
  FileCache& file_cache_;

  // TODO replace these with circular buffer class when it becomes available.
  std::unique_ptr<proto::MemoryData::MemorySample[]> memory_samples_;
  std::unique_ptr<proto::MemoryData::VmStatsSample[]> vm_stats_samples_;
  std::unique_ptr<proto::HeapDumpInfo[]> heap_dump_infos_;
  std::unique_ptr<proto::AllocationsInfo[]> allocations_info_;
  std::mutex memory_samples_mutex_;
  std::mutex vm_stats_samples_mutex_;
  std::mutex heap_dump_infos_mutex_;
  std::mutex allocations_info_mutex_;

  int32_t put_memory_sample_index_;
  int32_t put_vm_stats_sample_index_;
  int32_t next_heap_dump_sample_id_;
  int32_t next_allocations_info_id_;
  // TODO consider configuring cache sizes independently.
  int32_t samples_capacity_;

  bool memory_samples_buffer_full_;
  bool vm_stats_samples_buffer_full_;
  bool has_unfinished_heap_dump_;
  bool is_allocation_tracking_enabled_;
};

}  // namespace profiler

#endif  // MEMORY_CACHE_H_
