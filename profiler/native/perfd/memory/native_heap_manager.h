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
#ifndef PERFD_MEMORY_NATIVE_HEAP_MANAGER_H_
#define PERFD_MEMORY_NATIVE_HEAP_MANAGER_H_

#include "perfd/common/perfetto_manager.h"
#include "proto/transport.grpc.pb.h"
#include "utils/file_cache.h"

namespace profiler {
// Helper class to manage the starting and stopping of a heapprofd recording.
class NativeHeapManager {
 public:
  NativeHeapManager(FileCache* file_cache, PerfettoManager& perfetto_manager)
      : file_cache_(file_cache),
        perfetto_manager_(perfetto_manager),
        is_ongoing_capture_(false) {}

  ~NativeHeapManager();

  bool StartSample(int64_t ongoing_capture_id,
                   const proto::StartNativeSample& config,
                   std::string* error_message);
  bool StopSample(int64_t capture_id, std::string* error_message);

 private:
  FileCache* file_cache_;
  PerfettoManager& perfetto_manager_;
  std::string ongoing_capture_path_;
  bool is_ongoing_capture_;
  int64_t ongoing_capture_id_;
};

}  // namespace profiler

#endif  // PERFD_MEMORY_NATIVE_HEAP_MANAGER_H_
