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
#ifndef PERFD_CPU_CPU_CACHE_H_
#define PERFD_CPU_CPU_CACHE_H_

#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include "perfd/cpu/threads_sample.h"
#include "proto/cpu.grpc.pb.h"
#include "proto/cpu.pb.h"
#include "utils/time_value_buffer.h"

namespace profiler {

class CpuCache {
 public:
  // Return type of method "GetThreads". Contains the snapshot at the beginning
  // of a request range and the activitie that happened within that range.
  struct ThreadSampleResponse {
    profiler::proto::GetThreadsResponse::ThreadSnapshot snapshot;
    std::vector<ThreadsSample> activity_samples;
  };

  // Construct the main CPU cache holder. |capacity| is of every app's every
  // kind of cache (same size for all).
  explicit CpuCache(int32_t capacity) : capacity_(capacity) {}

  // Returns true if successfully allocating a cache for a given app, or if
  // the cache is already allocated.
  bool AllocateAppCache(int32_t app_id);
  // Returns true if successfully deallocating the cache for a given app.
  bool DeallocateAppCache(int32_t app_id);

  // Returns true if successfully adding |datum| to the cache.
  bool Add(const profiler::proto::CpuProfilerData& datum);
  // Retrieves data of |app_id| with timestamps in interval (|from|, |to|].
  // TODO: Support proto::AppId::ANY.
  std::vector<profiler::proto::CpuProfilerData> Retrieve(int32_t app_id,
                                                         int64_t from,
                                                         int64_t to);

  // Returns true if successfully adding |threads_sample| to the cache.
  bool AddThreads(const ThreadsSample& threads_sample);
  // Gets thread samples data of |app_id| with timestamps in interval
  // (|from|, |to|].
  // TODO: Support proto::AppId::ANY.
  ThreadSampleResponse GetThreads(int32_t app_id, int64_t from, int64_t to);

 private:
  // Each app's cache held by CPU component in the on-device daemon.
  struct AppCpuCache {
    int32_t app_id;
    TimeValueBuffer<profiler::proto::CpuProfilerData> usage_cache;
    TimeValueBuffer<ThreadsSample> threads_cache;

    AppCpuCache(int32_t app_id, int32_t capacity)
        : app_id(app_id),
          usage_cache(capacity, app_id),
          threads_cache(capacity, app_id) {}
  };

  // Returns the raw pointer to the cache for a given app. Returns null if
  // it doesn't exist. No ownership transfer.
  AppCpuCache* FindAppCache(int32_t app_id);

  // Each app has a set of dedicated caches.
  std::vector<std::unique_ptr<AppCpuCache>> app_caches_;
  // The capacity of every kind of cache.
  int32_t capacity_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_CACHE_H_
