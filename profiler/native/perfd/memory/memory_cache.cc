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

#include <algorithm>

#include "utils/file_reader.h"
#include "utils/log.h"

using ::profiler::proto::TrackAllocationsResponse;
using ::profiler::proto::DumpDataResponse;
using ::profiler::proto::HeapDumpInfo;
using ::profiler::proto::MemoryData;

namespace {
  // Indicates that a heap dump is in progress.
  static const int64_t kUnfinishedTimestamp = -1;
}

namespace profiler {

MemoryCache::MemoryCache(const Clock& clock, int32_t samples_capacity)
    : clock_(clock),
      memory_samples_(new MemoryData::MemorySample[samples_capacity]),
      vm_stats_samples_(new MemoryData::VmStatsSample[samples_capacity]),
      heap_dump_infos_(new HeapDumpInfo[samples_capacity]),
      allocations_info_(new MemoryData::AllocationsInfo[samples_capacity]),
      put_memory_sample_index_(0),
      put_vm_stats_sample_index_(0),
      put_allocations_info_index_(0),
      next_heap_dump_sample_id_(0),
      samples_capacity_(samples_capacity),
      memory_samples_buffer_full_(false),
      vm_stats_samples_buffer_full_(false),
      allocations_info_buffer_full_(false),
      has_unfinished_heap_dump_(false),
      is_allocation_tracking_enabled_(false) {}

void MemoryCache::SaveMemorySample(const MemoryData::MemorySample& sample) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);

  memory_samples_[put_memory_sample_index_].CopyFrom(sample);
  memory_samples_[put_memory_sample_index_].set_timestamp(
      clock_.GetCurrentTime());
  put_memory_sample_index_ = GetNextSampleIndex(put_memory_sample_index_);
  if (put_memory_sample_index_ == 0) {
    memory_samples_buffer_full_ = true;  // Check if we have wrapped.
  }
}

void MemoryCache::SaveVmStatsSample(const MemoryData::VmStatsSample& sample) {
  std::lock_guard<std::mutex> lock(vm_stats_samples_mutex_);

  vm_stats_samples_[put_vm_stats_sample_index_].CopyFrom(sample);
  put_vm_stats_sample_index_ = GetNextSampleIndex(put_vm_stats_sample_index_);
  if (put_vm_stats_sample_index_ == 0) {
    vm_stats_samples_buffer_full_ = true;
  }
}

bool MemoryCache::StartHeapDump(const std::string& dump_file_path,
                                int64_t request_time) {
  std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);

  if (has_unfinished_heap_dump_) {
    Log::D("StartHeapDumpSample called with existing unfinished heap dump.");
    return false;
  }

  HeapDumpInfo* info = &heap_dump_infos_[next_heap_dump_sample_id_];
  info->set_start_time(request_time);
  info->set_end_time(kUnfinishedTimestamp);
  info->set_dump_id(next_heap_dump_sample_id_);
  info->set_file_path(dump_file_path);

  next_heap_dump_sample_id_++;
  has_unfinished_heap_dump_ = true;

  // TODO remove previous heap dump files if buffer is full.

  return true;
}

bool MemoryCache::EndHeapDump(int64_t end_time, bool success) {
  std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);

  if (!has_unfinished_heap_dump_) {
    Log::D("CompleteHeapDumpSample called with no unfinished heap dump.");
    return false;
  }

  // Gets the last HeapDumpInfo and sets its end time.
  int last_heap_dump_sample_index = GetSampleIndex(next_heap_dump_sample_id_ - 1);
  heap_dump_infos_[last_heap_dump_sample_index].set_end_time(end_time);
  heap_dump_infos_[last_heap_dump_sample_index].set_success(success);
  has_unfinished_heap_dump_ = false;

  return true;
}

void MemoryCache::TrackAllocations(bool enabled,
                                   TrackAllocationsResponse* response) {
  std::lock_guard<std::mutex> lock(allocations_info_mutex_);

  if (enabled == is_allocation_tracking_enabled_) {
    if (is_allocation_tracking_enabled_) {
      response->set_status(TrackAllocationsResponse::IN_PROGRESS);
    } else {
      response->set_status(TrackAllocationsResponse::NOT_ENABLED);
    }
  } else {
    int64_t timestamp = clock_.GetCurrentTime();
    if (enabled) {
      MemoryData::AllocationsInfo& info =
          allocations_info_[put_allocations_info_index_];
      info.set_start_time(timestamp);
      info.set_end_time(kUnfinishedTimestamp);
      put_allocations_info_index_ = GetNextSampleIndex(
          put_allocations_info_index_);
      if (put_allocations_info_index_ == 0) {
        allocations_info_buffer_full_ = true;  // Check if we have wrapped.
      }
    } else {
      allocations_info_[put_allocations_info_index_ == 0 ?
          samples_capacity_ - 1 : put_allocations_info_index_ - 1]
          .set_end_time(timestamp);
    }
    response->set_status(TrackAllocationsResponse::SUCCESS);
    response->set_timestamp(timestamp);
    is_allocation_tracking_enabled_ = enabled;
    // TODO enable/disable post-O allocation tracking
  }
}

void MemoryCache::LoadMemoryData(int64_t start_time_exl, int64_t end_time_inc,
                                 MemoryData* response) {
  std::lock_guard<std::mutex> memory_lock(memory_samples_mutex_);
  std::lock_guard<std::mutex> vm_stats_lock(vm_stats_samples_mutex_);
  std::lock_guard<std::mutex> heap_dump_lock(heap_dump_infos_mutex_);
  std::lock_guard<std::mutex> allocations_info_lock(allocations_info_mutex_);

  int64_t end_timestamp = -1;
  if (put_memory_sample_index_ > 0 || memory_samples_buffer_full_) {
    int32_t i = memory_samples_buffer_full_ ? put_memory_sample_index_ : 0;
    do {
      int64_t timestamp = memory_samples_[i].timestamp();
      // TODO add optimization to skip past already-queried entries if the array
      // gets large.
      if (timestamp > start_time_exl && timestamp <= end_time_inc) {
        response->add_mem_samples()->CopyFrom(memory_samples_[i]);
        end_timestamp = std::max(timestamp, end_timestamp);
      }
      i = GetNextSampleIndex(i);
    } while (i != put_memory_sample_index_);
  }

  if (put_vm_stats_sample_index_ > 0 || vm_stats_samples_buffer_full_) {
    int32_t i = vm_stats_samples_buffer_full_ ? put_vm_stats_sample_index_ : 0;
    do {
      int64_t timestamp = vm_stats_samples_[i].timestamp();
      if (timestamp > start_time_exl && timestamp <= end_time_inc) {
        response->add_vm_stats_samples()->CopyFrom(vm_stats_samples_[i]);
        end_timestamp = std::max(timestamp, end_timestamp);
      }
      i = GetNextSampleIndex(i);
    } while (i != put_vm_stats_sample_index_);
  }

  if (put_allocations_info_index_ > 0 || allocations_info_buffer_full_) {
    int32_t i = allocations_info_buffer_full_ ?
        put_allocations_info_index_ : 0;
    do {
      int64_t start_time = allocations_info_[i].start_time();
      int64_t end_time = allocations_info_[i].end_time();
      if ((start_time > start_time_exl && start_time <= end_time_inc) ||
          (end_time > start_time_exl && end_time <= end_time_inc)) {
        response->add_allocations_info()->CopyFrom(allocations_info_[i]);
        end_timestamp = std::max({start_time, end_time, end_timestamp});
      }
      i = GetNextSampleIndex(i);
    } while (i != put_allocations_info_index_);
  }

  // TODO implement JVMTI allocation events AND MAKE SURE THE BUFFER IS LARGE

  if (next_heap_dump_sample_id_ > 0) {
    // Since |next_heap_dump_sample_id_| is a contiguous value mapped to a
    // wrapping index, we'll start the search from 0 (if we have accumulated
    // less samples than |samples_capacity_|), or from |samples_capacity_| ago.
    int32_t search_id = std::max(next_heap_dump_sample_id_ - samples_capacity_,
                                 0);
    while (search_id < next_heap_dump_sample_id_) {
      int32_t i = GetSampleIndex(search_id);
      int64_t start_time = heap_dump_infos_[i].start_time();
      int64_t end_time = heap_dump_infos_[i].end_time();
      // Include heap dump samples that have started/ended between
      // start_time_exl and end_time_inc
      if ((start_time > start_time_exl && start_time <= end_time_inc) ||
          (end_time > start_time_exl && end_time <= end_time_inc)) {
        response->add_heap_dump_infos()->CopyFrom(heap_dump_infos_[i]);
        end_timestamp = std::max({start_time, end_time, end_timestamp});
      }
      search_id++;
    }
  }

  response->set_end_timestamp(end_timestamp);
}

void MemoryCache::ReadHeapDumpFileContents(int32_t dump_id,
                                           DumpDataResponse* response) {
  std::string heap_dump_file_path;
  {
    std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);
    int32_t index = GetSampleIndex(dump_id);
    if (heap_dump_infos_[index].dump_id() != dump_id) {
      response->set_status(DumpDataResponse::NOT_FOUND);
      return;
    } else if (heap_dump_infos_[index].end_time() == kUnfinishedTimestamp) {
      response->set_status(DumpDataResponse::NOT_READY);
      return;
    } else {
      heap_dump_file_path.assign(heap_dump_infos_[index].file_path());
    }
  }
  FileReader::Read(heap_dump_file_path, response->mutable_data());
  response->set_status(DumpDataResponse::SUCCESS);
}

int MemoryCache::GetSampleIndex(int32_t id) {
  return id % samples_capacity_;
}

int MemoryCache::GetNextSampleIndex(int32_t id) {
  return (id + 1) % samples_capacity_;
}

}  // namespace profiler
