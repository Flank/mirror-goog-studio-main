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

#include <gtest/gtest.h>

#include "proto/memory.pb.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"

using profiler::proto::AllocationSamplingRateEvent;
using profiler::proto::AllocationsInfo;
using profiler::proto::BatchAllocationContexts;
using profiler::proto::BatchAllocationEvents;
using profiler::proto::BatchJNIGlobalRefEvent;
using profiler::proto::HeapDumpStatus;
using profiler::proto::MemoryData;
using profiler::proto::TrackAllocationsResponse;
using profiler::proto::TrackStatus;
using profiler::proto::TriggerHeapDumpResponse;

const int64_t profiler::MemoryCache::kUnfinishedTimestamp;

TEST(MemoryCache, TrackAllocations) {
  profiler::FakeClock fake_clock(0);
  profiler::MemoryCache cache(&fake_clock, 2);
  TrackAllocationsResponse response;

  // Ensure stopping does nothing if no current tracking is enabled.
  cache.TrackAllocations(0, false, false, &response);
  EXPECT_EQ(TrackStatus::NOT_ENABLED, response.status().status());

  // Begin a tracking session at t=5.
  cache.TrackAllocations(5, true, false, &response);
  EXPECT_EQ(5, response.status().start_time());
  EXPECT_EQ(TrackStatus::SUCCESS, response.status().status());
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_FALSE(response.info().success());
  EXPECT_FALSE(response.info().legacy());

  // Ensures enabling tracking while one is already in progress
  // does nothing.
  cache.TrackAllocations(5, true, false, &response);
  EXPECT_EQ(TrackStatus::IN_PROGRESS, response.status().status());

  // Complete a tracking session at t=10.
  cache.TrackAllocations(10, false, false, &response);
  EXPECT_EQ(5, response.status().start_time());
  EXPECT_EQ(TrackStatus::SUCCESS, response.status().status());
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(10, response.info().end_time());
  EXPECT_FALSE(response.info().legacy());
  EXPECT_TRUE(response.info().success());

  // Start a tracking session at t=10;
  cache.TrackAllocations(10, true, true, &response);
  EXPECT_EQ(10, response.status().start_time());
  EXPECT_EQ(TrackStatus::SUCCESS, response.status().status());
  EXPECT_EQ(10, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_FALSE(response.info().success());
  EXPECT_TRUE(response.info().legacy());

  // Ensure LoadMemoryData returns the correct info data.
  MemoryData data_response;
  cache.LoadMemoryData(0, 20, &data_response);
  EXPECT_EQ(2, data_response.allocations_info().size());

  EXPECT_EQ(5, data_response.allocations_info(0).start_time());
  EXPECT_EQ(10, data_response.allocations_info(0).end_time());
  EXPECT_FALSE(data_response.allocations_info(0).legacy());
  EXPECT_TRUE(data_response.allocations_info(0).success());

  EXPECT_EQ(10, data_response.allocations_info(1).start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            data_response.allocations_info(1).end_time());
  EXPECT_TRUE(data_response.allocations_info(1).legacy());
  EXPECT_FALSE(data_response.allocations_info(1).success());

  // Complete the tracking session at t=15
  cache.TrackAllocations(15, false, true, &response);
  EXPECT_EQ(10, response.status().start_time());
  EXPECT_EQ(TrackStatus::SUCCESS, response.status().status());
  EXPECT_EQ(10, response.info().start_time());
  EXPECT_EQ(15, response.info().end_time());
  EXPECT_TRUE(response.info().legacy());
  EXPECT_TRUE(response.info().success());

  // Validates LoadMemoryData again
  MemoryData data_response_2;
  cache.LoadMemoryData(10, 15, &data_response_2);
  EXPECT_EQ(1, data_response_2.allocations_info().size());
  EXPECT_EQ(10, data_response_2.allocations_info(0).start_time());
  EXPECT_EQ(15, data_response_2.allocations_info(0).end_time());
  EXPECT_TRUE(data_response_2.allocations_info(0).legacy());
  EXPECT_TRUE(data_response_2.allocations_info(0).success());
}

TEST(MemoryCache, HeapDump) {
  profiler::FakeClock fake_clock(0);
  profiler::MemoryCache cache(&fake_clock, 2);
  TriggerHeapDumpResponse response;

  // Ensure EndHeapDump does nothing if no in-progress heap dump
  EXPECT_EQ(false, cache.EndHeapDump(5, true));
  EXPECT_EQ(false, cache.EndHeapDump(5, false));

  // Triggers a heap dump
  bool success = cache.StartHeapDump(5, &response);
  EXPECT_EQ(true, success);
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_FALSE(response.info().success());

  // Ensure calling StartheapDump the second time fails and
  // returns the previous sample.
  success = cache.StartHeapDump(10, &response);
  EXPECT_EQ(false, success);
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_FALSE(response.info().success());

  // Completes a heap dump
  EXPECT_EQ(true, cache.EndHeapDump(15, true));

  // Triggers a second heap dump
  success = cache.StartHeapDump(20, &response);
  EXPECT_EQ(true, success);
  EXPECT_EQ(20, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_FALSE(response.info().success());

  // Ensures validity of the HeapDumpInfos returned via LoadMemoryData
  MemoryData data_response;
  cache.LoadMemoryData(10, 20, &data_response);
  EXPECT_EQ(2, data_response.heap_dump_infos().size());

  EXPECT_TRUE(data_response.heap_dump_infos(0).success());
  EXPECT_EQ(5, data_response.heap_dump_infos(0).start_time());
  EXPECT_EQ(15, data_response.heap_dump_infos(0).end_time());

  EXPECT_FALSE(data_response.heap_dump_infos(1).success());
  EXPECT_EQ(20, data_response.heap_dump_infos(1).start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            data_response.heap_dump_infos(1).end_time());
}

TEST(MemoryCache, GetMemoryJvmtiData) {
  profiler::FakeClock fake_clock(0);
  profiler::MemoryCache cache(&fake_clock, 2);
  MemoryData response;

  BatchAllocationEvents alloc_events;
  BatchAllocationContexts alloc_contexts;
  alloc_events.set_timestamp(1);
  alloc_contexts.set_timestamp(1);
  cache.SaveAllocationEvents(alloc_contexts, alloc_events);

  BatchJNIGlobalRefEvent jni_ref_event;
  jni_ref_event.set_timestamp(2);
  alloc_contexts.set_timestamp(2);
  cache.SaveJNIRefEvents(alloc_contexts, jni_ref_event);

  AllocationSamplingRateEvent event;
  event.set_timestamp(3);
  event.mutable_sampling_rate()->set_sampling_num_interval(10);
  cache.SaveAllocationSamplingRateEvent(event);

  cache.LoadMemoryJvmtiData(0, 3, &response);
  EXPECT_EQ(2, response.batch_allocation_contexts_size());
  EXPECT_EQ(1, response.batch_allocation_events_size());
  EXPECT_EQ(1, response.jni_reference_event_batches_size());
  EXPECT_EQ(1, response.alloc_sampling_rate_events_size());
  EXPECT_EQ(3, response.alloc_sampling_rate_events(0).timestamp());
  EXPECT_EQ(10, response.alloc_sampling_rate_events(0)
                    .sampling_rate()
                    .sampling_num_interval());
  EXPECT_EQ(3, response.end_timestamp());
}
