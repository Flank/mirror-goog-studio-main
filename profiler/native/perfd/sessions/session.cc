/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "session.h"

namespace profiler {

Session::Session(int64_t device_id, int32_t pid, int64_t start_timestamp) {
  // TODO: Revisit uniqueness of this:
  info.set_session_id(device_id ^ (start_timestamp << 1));
  info.set_device_id(device_id);
  info.set_pid(pid);
  info.set_start_timestamp(start_timestamp);
  info.set_end_timestamp(LLONG_MAX);
}

bool Session::IsActive() { return info.end_timestamp() == LLONG_MAX; }

bool Session::End(int64_t timestamp) {
  if (!IsActive()) {
    return false;
  }
  info.set_end_timestamp(timestamp);
  return true;
}

}  // namespace profiler
