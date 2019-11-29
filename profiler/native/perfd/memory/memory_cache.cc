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

using ::profiler::proto::AllocationEvent;
using ::profiler::proto::AllocationSamplingRateEvent;
using ::profiler::proto::AllocationsInfo;
using ::profiler::proto::BatchAllocationContexts;
using ::profiler::proto::BatchAllocationEvents;
using ::profiler::proto::BatchJNIGlobalRefEvent;
using ::profiler::proto::HeapDumpInfo;
using ::profiler::proto::HeapDumpStatus;
using ::profiler::proto::MemoryData;
using ::profiler::proto::TrackAllocationsResponse;
using ::profiler::proto::TrackStatus;
using ::profiler::proto::TriggerHeapDumpResponse;

namespace profiler {

// O+ allocation events data needs a larger buffer size as they are
// pushed from perfa instead of being sampled at a fixed interval in perfd.
// During initial heap snapshotting, there can potentially be a large
// amount of samples being pushed before Studio has a chance to query them.
// TODO: revisit whether this is too large.
constexpr int64_t kAllocDataCapacity = 500;

MemoryCache::MemoryCache(Clock* clock, int32_t samples_capacity)
    : clock_(clock),
      memory_samples_(samples_capacity),
      alloc_stats_samples_(samples_capacity),
      gc_stats_samples_(samples_capacity),
      heap_dump_infos_(samples_capacity),
      allocations_info_(samples_capacity),
      allocation_contexts_(kAllocDataCapacity),
      allocation_events_(kAllocDataCapacity),
      jni_refs_event_batches_(kAllocDataCapacity),
      alloc_sampling_rate_events_(kAllocDataCapacity),
      has_unfinished_heap_dump_(false),
      is_allocation_tracking_enabled_(false) {}

void MemoryCache::SaveMemorySample(const MemoryData::MemorySample& sample) {
  std::lock_guard<std::mutex> lock(memory_samples_mutex_);
  memory_samples_.Add(sample)->set_timestamp(clock_->GetCurrentTime());
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

void MemoryCache::SaveAllocationEvents(const BatchAllocationContexts& contexts,
                                       const BatchAllocationEvents& events) {
  std::lock_guard<std::mutex> lock(allocation_events_mutex_);
  allocation_contexts_.Add(contexts);
  allocation_events_.Add(events);
}

void MemoryCache::SaveJNIRefEvents(const BatchAllocationContexts& contexts,
                                   const BatchJNIGlobalRefEvent& events) {
  std::lock_guard<std::mutex> lock(jni_ref_batches_mutex_);
  allocation_contexts_.Add(contexts);
  jni_refs_event_batches_.Add(events);
}

void MemoryCache::SaveAllocationSamplingRateEvent(
    const AllocationSamplingRateEvent& event) {
  std::lock_guard<std::mutex> lock(alloc_sampling_rate_mutex_);
  alloc_sampling_rate_events_.Add(event);
}

bool MemoryCache::StartHeapDump(int64_t request_time,
                                TriggerHeapDumpResponse* response) {
  std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);

  if (has_unfinished_heap_dump_) {
    Log::D(Log::Tag::PROFILER,
           "StartHeapDumpSample called with existing unfinished heap dump.");
    assert(heap_dump_infos_.size() > 0);
    response->mutable_info()->CopyFrom(heap_dump_infos_.back());
    return false;
  }

  HeapDumpInfo info;
  info.set_start_time(request_time);
  info.set_end_time(kUnfinishedTimestamp);
  response->mutable_info()->CopyFrom(info);
  heap_dump_infos_.Add(info);

  has_unfinished_heap_dump_ = true;

  // TODO remove previous heap dump files if buffer is full.

  return true;
}

bool MemoryCache::EndHeapDump(int64_t end_time, bool success) {
  std::lock_guard<std::mutex> lock(heap_dump_infos_mutex_);

  if (!has_unfinished_heap_dump_) {
    Log::D(Log::Tag::PROFILER,
           "CompleteHeapDumpSample called with no unfinished heap dump.");
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

void MemoryCache::TrackAllocations(int64_t request_time, bool enabled,
                                   bool legacy,
                                   TrackAllocationsResponse* response) {
  std::lock_guard<std::mutex> lock(allocations_info_mutex_);

  auto* status = response->mutable_status();
  if (enabled == is_allocation_tracking_enabled_) {
    if (is_allocation_tracking_enabled_) {
      status->set_status(TrackStatus::IN_PROGRESS);
    } else {
      status->set_status(TrackStatus::NOT_ENABLED);
    }
  } else {
    if (enabled) {
      AllocationsInfo info;
      info.set_start_time(request_time);
      info.set_end_time(kUnfinishedTimestamp);
      info.set_legacy(legacy);

      allocations_info_.Add(info);
      response->mutable_info()->CopyFrom(info);

      status->set_start_time(request_time);
    } else {
      assert(allocations_info_.size() > 0);
      AllocationsInfo& info = allocations_info_.back();
      info.set_end_time(request_time);
      info.set_success(true);
      response->mutable_info()->CopyFrom(info);

      status->set_start_time(info.start_time());
    }
    status->set_status(TrackStatus::SUCCESS);
    is_allocation_tracking_enabled_ = enabled;
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
    const MemoryData::MemorySample& sample = memory_samples_.Get(i);
    int64_t timestamp = sample.timestamp();
    // TODO add optimization to skip past already-queried entries if the array
    // gets large.
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_mem_samples()->CopyFrom(sample);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < alloc_stats_samples_.size(); ++i) {
    const MemoryData::AllocStatsSample& sample = alloc_stats_samples_.Get(i);
    int64_t timestamp = sample.timestamp();
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_alloc_stats_samples()->CopyFrom(sample);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < gc_stats_samples_.size(); ++i) {
    const MemoryData::GcStatsSample& sample = gc_stats_samples_.Get(i);
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
      int64_t info_max_time =
          end_time == kUnfinishedTimestamp ? start_time : end_time;
      end_timestamp = std::max({info_max_time, end_timestamp});
    }
  }

  for (size_t i = 0; i < heap_dump_infos_.size(); ++i) {
    const HeapDumpInfo& info = heap_dump_infos_.Get(i);
    int64_t start_time = info.start_time();
    int64_t end_time = info.end_time();
    // Include heap dump samples that have started/ended between
    // start_time_exl and end_time_inc
    if ((start_time > start_time_exl && start_time <= end_time_inc) ||
        (end_time > start_time_exl && end_time <= end_time_inc)) {
      response->add_heap_dump_infos()->CopyFrom(info);
      int64_t info_max_time =
          end_time == kUnfinishedTimestamp ? start_time : end_time;
      end_timestamp = std::max({info_max_time, end_timestamp});
    }
  }

  response->set_end_timestamp(end_timestamp);
}

void MemoryCache::LoadMemoryJvmtiData(int64_t start_time_exl,
                                      int64_t end_time_inc,
                                      MemoryData* response) {
  std::lock_guard<std::mutex> alloc_lock(allocation_events_mutex_);
  std::lock_guard<std::mutex> jni_lock(jni_ref_batches_mutex_);
  std::lock_guard<std::mutex> sampling_range_lock(alloc_sampling_rate_mutex_);

  // O+ data only.
  int64_t end_timestamp = -1;
  for (size_t i = 0; i < allocation_events_.size(); ++i) {
    const BatchAllocationEvents& sample = allocation_events_.Get(i);
    int64_t timestamp = sample.timestamp();
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_batch_allocation_events()->CopyFrom(sample);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < allocation_contexts_.size(); ++i) {
    const BatchAllocationContexts& sample = allocation_contexts_.Get(i);
    int64_t timestamp = sample.timestamp();
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_batch_allocation_contexts()->CopyFrom(sample);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < jni_refs_event_batches_.size(); ++i) {
    const BatchJNIGlobalRefEvent& batch = jni_refs_event_batches_.Get(i);
    int64_t timestamp = batch.timestamp();
    if (timestamp > start_time_exl && timestamp <= end_time_inc) {
      response->add_jni_reference_event_batches()->CopyFrom(batch);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  for (size_t i = 0; i < alloc_sampling_rate_events_.size(); ++i) {
    const AllocationSamplingRateEvent& event =
        alloc_sampling_rate_events_.Get(i);
    int64_t timestamp = event.timestamp();
    if ((timestamp > start_time_exl && timestamp <= end_time_inc)) {
      response->add_alloc_sampling_rate_events()->CopyFrom(event);
      end_timestamp = std::max(timestamp, end_timestamp);
    }
  }

  response->set_end_timestamp(end_timestamp);
}

}  // namespace profiler
