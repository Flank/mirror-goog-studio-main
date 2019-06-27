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
#ifndef PERFD_MEMORY_HEAP_DUMP_MANAGER_H_
#define PERFD_MEMORY_HEAP_DUMP_MANAGER_H_

#include <atomic>
#include <cstdint>
#include <map>
#include <mutex>

#include "proto/transport.grpc.pb.h"
#include "utils/activity_manager.h"
#include "utils/clock.h"
#include "utils/file_cache.h"

namespace profiler {

struct HeapDumpData {
  std::atomic_bool is_running_;
  std::thread dump_thread_;
};

// Helper class to manage the starting and stopping of heap dump.
class HeapDumpManager {
 public:
  // ID of the last segment in an hprof file.
  static const char kHprofDumpEndTag = 0x2C;
  // The length of the last segment in an hprof file.
  // This consists of the tag |kHprofDumpEndTag|(1) + timestamp(4) + length(4)
  // for the data porition of the segment, which should always be zero.
  static const int32_t kHprofEndTagLength = 9;

  HeapDumpManager(FileCache* file_cache)
      : HeapDumpManager(file_cache, ActivityManager::Instance()) {}

  // For testing
  HeapDumpManager(FileCache* file_cache, ActivityManager* activity_manager)
      : file_cache_(file_cache), activity_manager_(activity_manager) {}

  ~HeapDumpManager();

  bool TriggerHeapDump(int32_t pid, int64_t dump_id,
                       const std::function<void(bool)>& callback);

 private:
  void HeapDumpMain(int32_t pid, std::shared_ptr<File> file,
                    const std::function<void(bool)>& callback);

  FileCache* file_cache_;
  ActivityManager* activity_manager_;

  // Only allow one heap dump to be triggered at a time.
  std::mutex dump_mutex_;
  // Per-pid heap dump cache.
  std::map<int32_t, HeapDumpData> dump_map_;
};

}  // namespace profiler

#endif  // PERFD_MEMORY_HEAP_DUMP_MANAGER_H_
