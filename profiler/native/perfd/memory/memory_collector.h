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
#ifndef MEMORY_COLLECTOR_H_
#define MEMORY_COLLECTOR_H_

#include "memory_cache.h"
#include "memory_usage_reader_impl.h"
#include "proto/memory.grpc.pb.h"
#include "utils/clock.h"
#include "utils/file_cache.h"

#include <atomic>
#include <string>
#include <thread>

namespace profiler {

class MemoryCollector {
 private:
  static constexpr int64_t kSleepNs = Clock::ms_to_ns(250);
  static const int64_t kSecondsToBuffer = 5;
  static constexpr int64_t kSamplesCount =
      1 + Clock::s_to_ms(kSecondsToBuffer) / Clock::ns_to_ms(kSleepNs);

 public:
  MemoryCollector(int32_t pid, Clock* clock, FileCache* file_cache)
      : memory_cache_(clock, file_cache, kSamplesCount), pid_(pid) {}
  ~MemoryCollector();

  void Start();
  void Stop();
  bool IsRunning();
  void TrackAllocations(int64_t request_time, bool enabled, bool legacy,
                        proto::TrackAllocationsResponse* response);
  MemoryCache* memory_cache() { return &memory_cache_; }

 private:
  MemoryCache memory_cache_;
  MemoryUsageReaderImpl memory_usage_reader_;
  std::thread server_thread_;
  std::atomic_bool is_running_{false};
  int32_t pid_;

  void CollectorMain();
};  // MemoryCollector

}  // namespace profiler

#endif  // MEMORY_COLLECTOR_H_
