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
using ::profiler::proto::TriggerHeapDumpResponse;

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

bool MemoryCollector::IsRunning() { return is_running_; }

void MemoryCollector::CollectorMain() {
  SetThreadName("Studio:PollMem");

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

bool MemoryCollector::TriggerHeapDump(TriggerHeapDumpResponse* response) {
  if (is_heap_dump_running_) {
    Log::V("A heap dump operation is already in progress.");
    return false;
  }

  if (!is_heap_dump_running_.exchange(true)) {
    int64_t request_time = clock_.GetCurrentTime();
    std::stringstream ss;
    ss << pid_ << "_" << request_time << ".hprof";
    auto file = file_cache_->GetFile(ss.str());

    if (!memory_cache_.StartHeapDump(file->name(), request_time, response)) {
      Log::V("StartHeapDumpSample failed.");
      return false;
    }

    if (heap_dump_thread_.joinable()) {
      heap_dump_thread_.join();
    }

    heap_dump_thread_ = std::thread([this, file] { this->HeapDumpMain(file); });
  }

  return true;
}

void MemoryCollector::HeapDumpMain(std::shared_ptr<File> file) {
  SetThreadName("Studio:HeapDump");

  std::string unusedOutput;
  ActivityManager* am = ActivityManager::Instance();

  bool result = am->TriggerHeapDump(pid_, file->path(), &unusedOutput);

  // Monitoring the file to catch close event when the heap dump is complete
  FileSystemNotifier notifier(file->path(), FileSystemNotifier::CLOSE);
  if (!notifier.IsReadyToNotify() || !notifier.WaitUntilEventOccurs()) {
    Log::V("Unable to monitor heap dump file for completion");
    result = false;
  }

  if (!memory_cache_.EndHeapDump(clock_.GetCurrentTime(), result)) {
    Log::V("EndHeapDumpSample failed.");
  }

  is_heap_dump_running_.exchange(false);
}

void MemoryCollector::GetHeapDumpData(int64_t dump_time,
                                      DumpDataResponse* response) {
  memory_cache_.ReadHeapDumpFileContents(dump_time, response);
}

void MemoryCollector::TrackAllocations(int64_t request_time, bool enabled,
                                       bool legacy,
                                       TrackAllocationsResponse* response) {
  memory_cache_.TrackAllocations(request_time, enabled, legacy, response);
}

}  // namespace profiler
