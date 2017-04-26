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
#include "proto/memory.pb.h"
#include "utils/fake_clock.h"
#include "utils/file_cache.h"
#include "utils/fs/memory_file_system.h"

#include <gtest/gtest.h>

using profiler::proto::MemoryData;
using profiler::proto::AllocationsInfo;
using profiler::proto::TrackAllocationsResponse;
using profiler::proto::TriggerHeapDumpResponse;

const int64_t profiler::MemoryCache::kUnfinishedTimestamp;

TEST(MemoryCache, TrackAllocations) {
  profiler::FileCache file_cache(
      std::unique_ptr<profiler::FileSystem>(new profiler::MemoryFileSystem()));
  profiler::FakeClock fake_clock(0);
  profiler::MemoryCache cache(fake_clock, &file_cache, 2);
  TrackAllocationsResponse response;

  // Ensure stopping does nothing if no current tracking is enabled.
  cache.TrackAllocations(0, false, false, &response);
  EXPECT_EQ(TrackAllocationsResponse::NOT_ENABLED, response.status());

  // Begin a tracking session at t=5.
  cache.TrackAllocations(5, true, false, &response);
  EXPECT_EQ(TrackAllocationsResponse::SUCCESS, response.status());
  EXPECT_EQ(AllocationsInfo::IN_PROGRESS, response.info().status());
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_EQ(false, response.info().legacy());

  // Ensures enabling tracking while one is already in progress
  // does nothing.
  cache.TrackAllocations(5, true, false, &response);
  EXPECT_EQ(TrackAllocationsResponse::IN_PROGRESS, response.status());

  // Complete a tracking session at t=10.
  cache.TrackAllocations(10, false, false, &response);
  EXPECT_EQ(TrackAllocationsResponse::SUCCESS, response.status());
  EXPECT_EQ(AllocationsInfo::COMPLETED, response.info().status());
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(10, response.info().end_time());
  EXPECT_EQ(false, response.info().legacy());

  // Start a tracking session at t=10;
  cache.TrackAllocations(10, true, true, &response);
  EXPECT_EQ(TrackAllocationsResponse::SUCCESS, response.status());
  EXPECT_EQ(AllocationsInfo::IN_PROGRESS, response.info().status());
  EXPECT_EQ(10, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_EQ(true, response.info().legacy());

  // Ensure LoadMemoryData returns the correct info data.
  MemoryData data_response;
  cache.LoadMemoryData(0, 20, &data_response);
  EXPECT_EQ(2, data_response.allocations_info().size());

  EXPECT_EQ(AllocationsInfo::COMPLETED,
            data_response.allocations_info(0).status());
  EXPECT_EQ(5, data_response.allocations_info(0).start_time());
  EXPECT_EQ(10, data_response.allocations_info(0).end_time());
  EXPECT_EQ(false, data_response.allocations_info(0).legacy());

  EXPECT_EQ(AllocationsInfo::IN_PROGRESS,
            data_response.allocations_info(1).status());
  EXPECT_EQ(10, data_response.allocations_info(1).start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            data_response.allocations_info(1).end_time());
  EXPECT_EQ(true, data_response.allocations_info(1).legacy());

  // Complete the tracking session at t=15
  cache.TrackAllocations(15, false, true, &response);
  EXPECT_EQ(TrackAllocationsResponse::SUCCESS, response.status());
  EXPECT_EQ(AllocationsInfo::COMPLETED, response.info().status());
  EXPECT_EQ(10, response.info().start_time());
  EXPECT_EQ(15, response.info().end_time());
  EXPECT_EQ(true, response.info().legacy());

  // Validates LoadMemoryData again
  MemoryData data_response_2;
  cache.LoadMemoryData(10, 15, &data_response_2);
  EXPECT_EQ(1, data_response_2.allocations_info().size());
  EXPECT_EQ(AllocationsInfo::COMPLETED,
            data_response_2.allocations_info(0).status());
  EXPECT_EQ(10, data_response_2.allocations_info(0).start_time());
  EXPECT_EQ(15, data_response_2.allocations_info(0).end_time());
  EXPECT_EQ(true, data_response_2.allocations_info(0).legacy());
}

TEST(MemoryCache, HeapDump) {
  profiler::FileCache file_cache(
      std::unique_ptr<profiler::FileSystem>(new profiler::MemoryFileSystem()));
  profiler::FakeClock fake_clock(0);
  profiler::MemoryCache cache(fake_clock, &file_cache, 2);
  TriggerHeapDumpResponse response;

  // Ensure EndHeapDump does nothing if no in-progress heap dump
  EXPECT_EQ(false, cache.EndHeapDump(5, true));
  EXPECT_EQ(false, cache.EndHeapDump(5, false));

  // Triggers a heap dump
  bool success = cache.StartHeapDump("dummy_path", 5, &response);
  EXPECT_EQ(true, success);
  EXPECT_EQ("dummy_path", response.info().file_name());
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_EQ(false, response.info().success());

  // Ensure calling StartheapDump the second time fails and
  // returns the previous sample.
  success = cache.StartHeapDump("dummy_path2", 10, &response);
  EXPECT_EQ(false, success);
  EXPECT_EQ("dummy_path", response.info().file_name());
  EXPECT_EQ(5, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());

  // Completes a heap dump
  EXPECT_EQ(true, cache.EndHeapDump(15, true));

  // Triggers a second heap dump
  success = cache.StartHeapDump("dummy_path2", 20, &response);
  EXPECT_EQ(true, success);
  EXPECT_EQ("dummy_path2", response.info().file_name());
  EXPECT_EQ(20, response.info().start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            response.info().end_time());
  EXPECT_EQ(false, response.info().success());

  // Ensures validity of the HeapDumpInfos returned via LoadMemoryData
  MemoryData data_response;
  cache.LoadMemoryData(10, 20, &data_response);
  EXPECT_EQ(2, data_response.heap_dump_infos().size());

  EXPECT_EQ(true, data_response.heap_dump_infos(0).success());
  EXPECT_EQ(5, data_response.heap_dump_infos(0).start_time());
  EXPECT_EQ(15, data_response.heap_dump_infos(0).end_time());

  EXPECT_EQ(false, data_response.heap_dump_infos(1).success());
  EXPECT_EQ(20, data_response.heap_dump_infos(1).start_time());
  EXPECT_EQ(profiler::MemoryCache::kUnfinishedTimestamp,
            data_response.heap_dump_infos(1).end_time());
}
