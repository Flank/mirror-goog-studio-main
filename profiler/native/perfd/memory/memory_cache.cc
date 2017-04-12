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
#include <cassert>

#include "utils/log.h"

using ::profiler::proto::TrackAllocationsResponse;
using ::profiler::proto::DumpDataResponse;
using ::profiler::proto::HeapDumpInfo;
using ::profiler::proto::MemoryData;
using ::profiler::proto::AllocationsInfo;
using ::profiler::proto::TriggerHeapDumpResponse;

namespace profiler {

MemoryCache::MemoryCache(const Clock& clock, FileCache* file_cache,
                         int32_t samples_capacity)
    : clock_(clock),
      file_cache_(file_cache),
      memory_samples_(samples_capacity),
      alloc_stats_samples_(samples_capacity),
      gc_stats_samples_(samples_capacity),
      heap_dump_infos_(samples_capacity),
      allocations_info_(samples_capacity),
      has_unfinished_heap_dump_(false),
      is_allocation_tracking_enabled_(false) {}

void MemoryCache::SaveMemorySample(const MemoryData::MemorySample& sample) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);
  memory_samples_.Add(sample)->set_timestamp(clock_.GetCurrentTime());
}

void MemoryCache::SaveAllocStatsSample(
    const MemoryData::AllocStatsSample& sample) {
  std::lock_guard<std::mutex> lock(alloc_stats_samples_mutex_);
  alloc_stats_samples_.Add(sample);
}

void MemoryCache::SaveGcStatsSample(const MemoryData::GcStatsSample& sample) {
  std::lock_guard<std::mutex> lock(gc_stats_samples_mutex_);
  gc_stats_samples_.Add(sample);
}

bool MemoryCache::StartHeapDump(const std::string& dump_file_name,
                                int64_t request_time,
                                TriggerHeapDumpResponse* response) {
  std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);

  if (has_unfinished_heap_dump_) {
    Log::D("StartHeapDumpSample called with existing unfinished heap dump.");
    assert(heap_dump_infos_.size() > 0);
    response->mutable_info()->CopyFrom(heap_dump_infos_.back());
    return false;
  }

  HeapDumpInfo info;
  info.set_start_time(request_time);
  info.set_end_time(kUnfinishedTimestamp);
  info.set_file_name(dump_file_name);
  response->mutable_info()->CopyFrom(info);
  heap_dump_infos_.Add(info);

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
  assert(heap_dump_infos_.size() > 0);
  HeapDumpInfo& info = heap_dump_infos_.back();
  info.set_end_time(end_time);
  info.set_success(success);
  has_unfinished_heap_dump_ = false;

  return true;
}

void MemoryCache::TrackAllocations(bool enabled, bool legacy,
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
      AllocationsInfo info;
      info.set_start_time(timestamp);
      info.set_end_time(kUnfinishedTimestamp);
      info.set_legacy(legacy);
      info.set_status(AllocationsInfo::IN_PROGRESS);

      allocations_info_.Add(info);
      response->mutable_info()->CopyFrom(info);
    } else {
      assert(allocations_info_.size() > 0);
      AllocationsInfo& info = allocations_info_.back();
      info.set_end_time(timestamp);
      info.set_status(AllocationsInfo::COMPLETED);
      response->mutable_info()->CopyFrom(info);
    }
    response->set_status(TrackAllocationsResponse::SUCCESS);
    is_allocation_tracking_enabled_ = enabled;
    // TODO enable/disable post-O allocation tracking
  }
}

void MemoryCache::LoadMemoryData(int64_t start_time_exl, int64_t end_time_inc,
                                 MemoryData* response) {
  std::lock_guard<std::mutex> memory_lock(memory_samples_mutex_);
  std::lock_guard<std::mutex> alloc_stats_lock(alloc_stats_samples_mutex_);
  std::lock_guard<std::mutex> gc_stats_lock(gc_stats_samples_mutex_);
  std::lock_guard<std::mutex> heap_dump_lock(heap_dump_infos_mutex_);
  std::lock_guard<std::mutex> allocations_info_lock(allocations_info_mutex_);

  int64_t end_timestamp = -1;
  for (size_t i = 0; i < memory_samples_.size(); ++i) {
    const proto::MemoryData::MemorySample& sample = memory_samples_.Get(i);
    int64_t timestamp = sample.timestamp();
    // TODO add optimization to skip past already-queried entries if the array
    // gets large.
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_mem_samples()->CopyFrom(sample);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < alloc_stats_samples_.size(); ++i) {
    const proto::MemoryData::AllocStatsSample& sample =
        alloc_stats_samples_.Get(i);
    int64_t timestamp = sample.timestamp();
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_alloc_stats_samples()->CopyFrom(sample);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < gc_stats_samples_.size(); ++i) {
    const proto::MemoryData::GcStatsSample& sample = gc_stats_samples_.Get(i);
    int64_t start_time = sample.start_time();
    int64_t end_time = sample.end_time();
    if ((start_time > start_time_exl && start_time <= end_time_inc) ||
        (end_time > start_time_exl && end_time <= end_time_inc)) {
      response->add_gc_stats_samples()->CopyFrom(sample);
      end_timestamp = std::max(end_time, end_timestamp);
    }
  }

  for (size_t i = 0; i < allocations_info_.size(); ++i) {
    const AllocationsInfo& info = allocations_info_.Get(i);
    int64_t start_time = info.start_time();
    int64_t end_time = info.end_time();
    if ((start_time > start_time_exl && start_time <= end_time_inc) ||
        (end_time > start_time_exl && end_time <= end_time_inc)) {
      response->add_allocations_info()->CopyFrom(info);
      end_timestamp = std::max({start_time, end_time, end_timestamp});
    }
  }

  // TODO implement JVMTI allocation events AND MAKE SURE THE BUFFER IS LARGE

  for (size_t i = 0; i < heap_dump_infos_.size(); ++i) {
    const HeapDumpInfo& info = heap_dump_infos_.Get(i);
    int64_t start_time = info.start_time();
    int64_t end_time = info.end_time();
    // Include heap dump samples that have started/ended between
    // start_time_exl and end_time_inc
    if ((start_time > start_time_exl && start_time <= end_time_inc) ||
        (end_time > start_time_exl && end_time <= end_time_inc)) {
      response->add_heap_dump_infos()->CopyFrom(info);
      end_timestamp = std::max({start_time, end_time, end_timestamp});
    }
  }

  response->set_end_timestamp(end_timestamp);
}

void MemoryCache::ReadHeapDumpFileContents(int64_t dump_time,
                                           DumpDataResponse* response) {
  std::string heap_dump_file_name;
  {
    std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);

    bool found = false;
    size_t found_index = -1;
    for (size_t i = 0; i < heap_dump_infos_.size(); ++i) {
      if (heap_dump_infos_.Get(i).start_time() == dump_time) {
        found = true;
        found_index = i;
        break;
      }
    }

    if (found) {
      const HeapDumpInfo& info = heap_dump_infos_.Get(found_index);
      if (info.end_time() == kUnfinishedTimestamp) {
        response->set_status(DumpDataResponse::NOT_READY);
        return;
      } else {
        heap_dump_file_name.assign(info.file_name());
      }
    } else {
      response->set_status(DumpDataResponse::NOT_FOUND);
      return;
    }
  }

  auto file = file_cache_->GetFile(heap_dump_file_name);
  response->mutable_data()->append(file->Contents());
  response->set_status(DumpDataResponse::SUCCESS);
}

}  // namespace profiler
