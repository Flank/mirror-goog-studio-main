/*
 * Copyright (C) 2017 The Android Open Source Project
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
#ifndef PERFD_IO_IO_SESSION_DETAILS_H_
#define PERFD_IO_IO_SESSION_DETAILS_H_

#include <string>
#include <vector>
#include "proto/io.grpc.pb.h"

namespace profiler {

struct IoSessionDetails final {
  struct IoCall {
    // timestamp when the call started
    int64_t start_timestamp;
    // timestamp when the call ended
    int64_t end_timestamp;
    // number of bytes read or written
    int32_t bytes_count;
    // The type of the operation (read or write).
    profiler::proto::IoType type;
  };

  // ID that can identify this session globally across all active apps.
  int64_t session_id = 0;
  // Time when this session started. This should always be set.
  int64_t start_timestamp = 0;
  // Time when the session was closed (either completed or aborted). This
  // value will be -1 until then.
  int64_t end_timestamp = -1;
  // The path to the file being read from or written into. This should always be
  // set.
  std::string file_path;
  // The reading or writing calls. Can be empty.
  std::vector<IoCall> calls;
};

}  // namespace profiler

#endif  // PERFD_IO_IO_SESSION_DETAILS_H_
