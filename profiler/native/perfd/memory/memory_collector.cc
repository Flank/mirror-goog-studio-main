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
#include <fstream>
#include <sstream>

#include "utils/activity_manager.h"
#include "utils/device_info.h"
#include "utils/filesystem_notifier.h"
#include "utils/log.h"
#include "utils/stopwatch.h"
#include "utils/thread_name.h"
#include "utils/trace.h"

using ::profiler::proto::DumpDataResponse;
using ::profiler::proto::TrackAllocationsResponse;
using ::profiler::proto::TriggerHeapDumpResponse;

namespace {
// ID of the last segment in an hprof file.
const char kHprofDumpEndTag = 0x2C;
// The length of the last segment in an hprof file.
// This consists of the tag |kHprofDumpEndTag|(1) + timestamp(4) + length(4)
// for the data porition of the segment, which should always be zero.
const int32_t kHprofEndTagLength = 9;
// By checking file size changing and the last piece of data in the dump file,
// we have no reliable way to distinguish between a heap dump failing vs user
// pausing in the debugger for a long time, or other potential failure cases.
// Stop trying if the file size has not changed for too long (5sec) and we don't
// see the end tag.
const int32_t kHprofReadRetryCount = 20;
const int64_t kHprofReadRetryIntervalUs = profiler::Clock::ms_to_us(250);

// In O, there is a bug in ActivityManagerService where the file descriptor
// associated with the dump file does not get closed until the next GC.
// This means we cannot use the inotify API to reliably detect when the dump
// event finishes. As a workaround, we wait for the file size to stablize
// AND check the last 9 bytes of the dump file to validate the  file ends
// with a HEAP DUMP END segment.
bool WaitForHeapDumpFinishInO(std::string file_path) {
  bool result = false;
  std::ifstream stream(file_path, std::ifstream::binary | std::ifstream::ate);
  if (stream.fail()) {
    profiler::Log::V("Failed to open hprof file stream.");
  } else {
    int retry = 0;
    int prev_size = -1;
    int curr_size = 0;
    do {
      usleep(static_cast<uint64_t>(kHprofReadRetryIntervalUs));
      prev_size = curr_size;
      stream.seekg(0, stream.end);
      curr_size = stream.tellg();
      result = prev_size == curr_size && curr_size > kHprofEndTagLength;
      if (curr_size != prev_size) {
        // Reset read retry count since file size is still changing.
        retry = 0;
      }

      // File size matched, check bytes
      if (result) {
        char buf[kHprofEndTagLength];
        stream.seekg(-kHprofEndTagLength, stream.end);
        stream.read(buf, kHprofEndTagLength);
        // First byte should be the tag, and the length as indicated by
        // an integer starting at bytes[5] should be zero. Endian-ness
        // does not matter in this case as we are reading a 0-value.
        int length = (buf[5] << 24) + (buf[6] << 16) + (buf[7] << 8) + buf[8];
        result = buf[0] == kHprofDumpEndTag && length == 0;
      }
    } while (!result && retry++ < kHprofReadRetryCount);
  }
  stream.close();

  return result;
}
}

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

  if (result) {
    if (profiler::DeviceInfo::feature_level() == 26) {
      result = WaitForHeapDumpFinishInO(file->path());
    } else {
      // Monitoring the file to catch close event when the heap dump is complete
      FileSystemNotifier notifier(file->path(), FileSystemNotifier::CLOSE);
      if (!notifier.IsReadyToNotify() || !notifier.WaitUntilEventOccurs(-1)) {
        Log::V("Unable to monitor heap dump file for completion");
        result = false;
      }
    }
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
