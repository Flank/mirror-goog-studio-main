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

#include "perfd/cpu/profiling_app.h"
#include "perfd/cpu/threads_sample.h"
#include "proto/cpu.grpc.pb.h"
#include "proto/cpu.pb.h"
#include "utils/clock.h"
#include "utils/file_cache.h"
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
  explicit CpuCache(int32_t capacity, Clock* clock, FileCache* file_cache)
      : capacity_(capacity), clock_(clock), file_cache_(file_cache) {}

  // Returns true if successfully allocating a cache for a given pid, or if
  // the cache is already allocated.
  bool AllocateAppCache(int32_t pid);
  // Returns true if successfully deallocating the cache for a given pid.
  bool DeallocateAppCache(int32_t pid);

  // Returns true if successfully adding |datum| to the cache.
  // If no active cache associated with the pid is found, no data would be saved
  // and this method returns false.
  bool Add(int32_t pid, const profiler::proto::CpuUsageData& datum);
  // Retrieves data of |pid| with timestamps in interval (|from|, |to|].
  std::vector<profiler::proto::CpuUsageData> Retrieve(int32_t pid, int64_t from,
                                                      int64_t to);

  // Returns true if successfully adding |threads_sample| to the cache.
  // If no active cache associated with the pid is found, no data would be saved
  // and this method eturns false.
  bool AddThreads(int32_t pid, const ThreadsSample& threads_sample);
  // Gets thread samples data of |pid| with timestamps in interval
  // (|from|, |to|].
  ThreadSampleResponse GetThreads(int32_t pid, int64_t from, int64_t to);

  // Adds start event for non-startup profiling. Returns a unique ID to identify
  // the profiling. Returns -1 if not successful.
  int32_t AddProfilingStart(int32_t pid, const ProfilingApp& record);
  // Adds stop event for non-startup profiling.
  bool AddProfilingStop(int32_t pid);
  // Adds start event for startup profiling. Returns a unique ID to identify the
  // profiling.
  int32_t AddStartupProfilingStart(const std::string& apk_pkg_name,
                                   const ProfilingApp& record);
  // Adds stop event for startup profiling.
  void AddStartupProfilingStop(const std::string& apk_pkg_name);

  // Returns the |ProfilingApp| of the app with the given |pid|.
  ProfilingApp* GetOngoingCapture(int32_t pid);

  // Returns the |ProfilingApp| of the app with the given |app_pkg_name| and
  // which was started by startup profiling.
  ProfilingApp* GetOngoingStartupProfiling(const std::string& app_pkg_name);

  // Returns the captures from process of |pid| that overlap with the given
  // interval [|from|, |to|], both inclusive.
  std::vector<ProfilingApp> GetCaptures(int32_t pid, int64_t from, int64_t to);
  // Returns true if successfully adding |trace_content| of the given |trace_id|
  // generated from app of given process ID |pid|.
  bool AddTraceContent(int32_t pid, int32_t trace_id,
                       const std::string& trace_content);
  // Returns true if successfully retrieve the content of given |trace_id| from
  // process of |pid|.
  bool RetrieveTraceContent(int32_t pid, int32_t trace_id, std::string* output);

 private:
  // Each app's cache held by CPU component in the on-device daemon.
  struct AppCpuCache {
    int32_t pid;
    TimeValueBuffer<profiler::proto::CpuUsageData> usage_cache;
    TimeValueBuffer<ThreadsSample> threads_cache;
    CircularBuffer<ProfilingApp> capture_cache;
    ProfilingApp* ongoing_capture;

    AppCpuCache(int32_t pid, int32_t capacity)
        : pid(pid),
          usage_cache(capacity, pid),
          threads_cache(capacity, pid),
          capture_cache(capacity),
          ongoing_capture(nullptr) {}
  };

  // Returns the raw pointer to the cache for a given app. Returns null if
  // it doesn't exist. No ownership transfer.
  AppCpuCache* FindAppCache(int32_t pid);

  // Returns a new positive integer that is unique throughout the lifetime of
  // this cache. It satisfies the uniqueness requirement for a trace ID (unique
  // within a session).
  int32_t GenerateTraceId();

  // Returns a unique, valid file name given the |pid| and |trace_id|.
  std::string GetCachedFileName(int32_t pid, int32_t trace_id);

  // Each app has a set of dedicated caches.
  std::vector<std::unique_ptr<AppCpuCache>> app_caches_;
  // The capacity of every kind of cache.
  int32_t capacity_;
  Clock* clock_;
  FileCache* file_cache_;

  // Map from app package name to the corresponding data of startup profiling.
  std::map<std::string, ProfilingApp> startup_profiling_apps_;
};

}  // namespace profiler

#endif  // PERFD_CPU_CPU_CACHE_H_
