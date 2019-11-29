/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "heap_dump_manager.h"

#include <unistd.h>

#include <cassert>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>

#include "utils/clock.h"
#include "utils/device_info.h"
#include "utils/filesystem_notifier.h"
#include "utils/log.h"
#include "utils/thread_name.h"

using profiler::Log;

namespace {
// By checking file size changing and the last piece of data in the dump file,
// we have no reliable way to distinguish between a heap dump failing vs user
// pausing in the debugger for a long time, or other potential failure cases.
// Stop trying if the file size has not changed for too long (5sec) and we don't
// see the end tag.
const int32_t kHprofReadRetryCount = 20;
const int64_t kHprofReadRetryIntervalUs = profiler::Clock::ms_to_us(250);

// In O+, there is a bug in ActivityManagerService where the file descriptor
// associated with the dump file does not get closed until the next GC.
// This means we cannot use the inotify API to reliably detect when the dump
// event finishes. As a workaround, we wait for the file size to stablize
// AND check the last 9 bytes of the dump file to validate the  file ends
// with a HEAP DUMP END segment.
bool WaitForHeapDumpFinishInOPlus(std::string file_path) {
  bool finished = false;
  std::ifstream stream(file_path, std::ifstream::binary | std::ifstream::ate);
  if (stream.fail()) {
    Log::V(Log::Tag::PROFILER, "Failed to open hprof file stream.");
  } else {
    int retry = 0;
    int prev_size = -1;
    int curr_size = 0;
    do {
      usleep(static_cast<uint64_t>(kHprofReadRetryIntervalUs));
      prev_size = curr_size;
      stream.seekg(0, stream.end);
      curr_size = stream.tellg();
      finished = (prev_size == curr_size) &&
                 (curr_size > profiler::HeapDumpManager::kHprofEndTagLength);
      if (curr_size != prev_size) {
        // Reset read retry count since file size is still changing.
        retry = 0;
      }

      // File size matched, check bytes
      if (finished) {
        char buf[profiler::HeapDumpManager::kHprofEndTagLength];
        stream.seekg(-profiler::HeapDumpManager::kHprofEndTagLength,
                     stream.end);
        stream.read(buf, profiler::HeapDumpManager::kHprofEndTagLength);
        // First byte should be the tag, and the length as indicated by
        // an integer starting at bytes[5] should be zero. Endian-ness
        // does not matter in this case as we are reading a 0-value.
        int length = (buf[5] << 24) + (buf[6] << 16) + (buf[7] << 8) + buf[8];
        finished = buf[0] == profiler::HeapDumpManager::kHprofDumpEndTag &&
                   length == 0;
      }
    } while (!finished && retry++ < kHprofReadRetryCount);
  }
  stream.close();
  return finished;
}
}  // namespace

namespace profiler {

HeapDumpManager::~HeapDumpManager() {
  std::lock_guard<std::mutex> lock(dump_mutex_);
  for (auto itr = dump_map_.begin(); itr != dump_map_.end(); itr++) {
    itr->second.is_running_.exchange(false);
    if (itr->second.dump_thread_.joinable()) {
      itr->second.dump_thread_.join();
    }
  }
}

bool HeapDumpManager::TriggerHeapDump(
    int32_t pid, int64_t dump_id, const std::function<void(bool)>& callback) {
  std::lock_guard<std::mutex> lock(dump_mutex_);

  auto result = dump_map_.emplace(std::piecewise_construct,
                                  std::make_tuple(pid), std::make_tuple());
  auto& data = result.first->second;
  if (data.is_running_) {
    Log::V(Log::Tag::PROFILER, "A heap dump for pid %d is already in progress.",
           pid);
    return false;
  }

  if (!data.is_running_.exchange(true)) {
    std::stringstream ss;
    ss << dump_id;
    auto file = file_cache_->GetFile(ss.str());

    if (data.dump_thread_.joinable()) {
      data.dump_thread_.join();
    }

    data.dump_thread_ = std::thread([this, pid, file, callback]() {
      this->HeapDumpMain(pid, file, callback);
    });
  }

  return true;
}

void HeapDumpManager::HeapDumpMain(int32_t pid, std::shared_ptr<File> file,
                                   const std::function<void(bool)>& callback) {
  SetThreadName("Studio:HeapDump");

  std::string unused;
  bool result = activity_manager_->TriggerHeapDump(pid, file->path(), &unused);
  if (result) {
    if (DeviceInfo::feature_level() >= DeviceInfo::O) {
      result = WaitForHeapDumpFinishInOPlus(file->path());
    } else {
      // Monitoring the file to catch close event when the heap dump is complete
      FileSystemNotifier notifier(file->path(), FileSystemNotifier::CLOSE);
      if (!notifier.IsReadyToNotify() || !notifier.WaitUntilEventOccurs(-1)) {
        Log::V(Log::Tag::PROFILER,
               "Unable to monitor heap dump file (pid=%d, path=%s) for "
               "completion",
               pid, file->path().c_str());
        result = false;
      }
    }
  }

  callback(result);
  {
    std::lock_guard<std::mutex> lock(dump_mutex_);
    auto itr = dump_map_.find(pid);
    assert(itr != dump_map_.end());
    itr->second.is_running_.exchange(false);
  }
}

}  // namespace profiler
