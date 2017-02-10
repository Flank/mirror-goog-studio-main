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

namespace profiler {

class CpuCache {
 public:
  // Return type of method "GetThreads". Contains the snapshot at the beginning
  // of a request range and the activitie that happened within that range.
  struct ThreadSampleResponse {
    profiler::proto::GetThreadsResponse::ThreadSnapshot snapshot;
    std::vector<ThreadsSample> activity_samples;
  };

  // Adds |datum| to the cache.
  void Add(const profiler::proto::CpuProfilerData& datum);

  // Retrieves data of |app_id| with timestamps in interval (|from|, |to|].
  // |app_id| being |kAnyApp| means all apps in the cache.
  std::vector<profiler::proto::CpuProfilerData> Retrieve(int32_t app_id,
                                                         int64_t from,
                                                         int64_t to);

  // Adds |threads_sample| to the cache.
  void AddThreads(const ThreadsSample& threads_sample);

  // Gets thread samples data of |app_id| with timestamps in interval
  // (|from|, |to|]. |app_id| being |kAnyApp| means all apps in the
  // cache.
  ThreadSampleResponse GetThreads(int32_t app_id, int64_t from, int64_t to);

 private:
  // TODO: Utilize something like a circular buffer. The size is unbounded for
  // now.
  std::vector<profiler::proto::CpuProfilerData> cache_;
  // Protects |cache_|.
  std::mutex cache_mutex_;

  std::vector<ThreadsSample> threads_cache_;
  // Protects |threads_cache_|.
  std::mutex threads_cache_mutex_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_CACHE_H_
