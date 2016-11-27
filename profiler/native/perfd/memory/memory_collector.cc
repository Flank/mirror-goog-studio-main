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
#include "memory_collector.h"

#include <unistd.h>
#include <sstream>

#include "utils/activity_manager.h"
#include "utils/filesystem_notifier.h"
#include "utils/log.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

using ::profiler::proto::DumpDataResponse;
using ::profiler::proto::TrackAllocationsResponse;

namespace profiler {

MemoryCollector::~MemoryCollector() { Stop(); }

void MemoryCollector::Start() {
  if (!is_running_.exchange(true)) {
    server_thread_ = std::thread([this] { this->CollectorMain(); });
  }
}

void MemoryCollector::Stop() {
  if (is_running_.exchange(false)) {
    server_thread_.join();
  }

  if (heap_dump_thread_.joinable()) {
    heap_dump_thread_.join();
  }
}

void MemoryCollector::CollectorMain() {
  SetThreadName("MemCollector");

  Stopwatch stopwatch;
  while (is_running_) {
    Trace::Begin("MEM:Collect");
    int64_t start_time_ns = stopwatch.GetElapsed();

    proto::MemoryData_MemorySample sample;
    memory_levels_sampler_.GetProcessMemoryLevels(pid_, &sample);
    memory_cache_.SaveMemorySample(sample);

    Trace::End();
    int64_t elapsed_time_ns = stopwatch.GetElapsed() - start_time_ns;
    if (kSleepNs > elapsed_time_ns) {
      int64_t sleep_time_us = Clock::ns_to_us(kSleepNs - elapsed_time_ns);
      usleep(static_cast<uint64_t>(sleep_time_us));
    }
  }

  is_running_.exchange(false);
}

bool MemoryCollector::TriggerHeapDump() {
  if (is_heap_dump_running_) {
    Log::V("A heap dump operation is already in progress.");
    return false;
  }

  if (!is_heap_dump_running_.exchange(true)) {
    int64_t request_time = clock_.GetCurrentTime();
    std::stringstream ss;
    ss << "/data/local/tmp/" << pid_ << "_" << request_time << ".hprof";
    std::string dump_file_path = ss.str();

    if (!memory_cache_.StartHeapDump(dump_file_path, request_time)) {
      Log::V("StartHeapDumpSample failed.");
      return false;
    }

    if (heap_dump_thread_.joinable()) {
      heap_dump_thread_.join();
    }
    heap_dump_thread_ = std::thread(
        [this, dump_file_path] { this->HeapDumpMain(dump_file_path); });
  }

  return true;
}

void MemoryCollector::HeapDumpMain(const std::string& file_path) {
  SetThreadName("HeapDump");

  std::string unusedOutput;
  ActivityManager* am = ActivityManager::Instance();

  bool result = am->TriggerHeapDump(pid_, file_path, &unusedOutput);

  // Monitoring the file_path to catch file close event when
  // the heap dump is complete
  FileSystemNotifier notifier(file_path, FileSystemNotifier::CLOSE);
  if (!notifier.IsReadyToNotify() || !notifier.WaitUntilEventOccurs()) {
    Log::V("Unable to monitor heap dump file for completion");
    result = false;
  }

  if (!memory_cache_.EndHeapDump(clock_.GetCurrentTime(), result)) {
    Log::V("EndHeapDumpSample failed.");
  }

  is_heap_dump_running_.exchange(false);
}

void MemoryCollector::GetHeapDumpData(int32_t dump_id,
                                      DumpDataResponse* response) {
  memory_cache_.ReadHeapDumpFileContents(dump_id, response);
}

void MemoryCollector::TrackAllocations(
    bool enabled,
    TrackAllocationsResponse* response) {
  memory_cache_.TrackAllocations(enabled, response);
}

}  // namespace profiler
