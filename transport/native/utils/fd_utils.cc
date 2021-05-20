/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include "utils/fd_utils.h"

#include <errno.h>
#include <string.h>
#include <unistd.h>

#include "utils/log.h"

namespace profiler {

int CloseFdAndLogAtError(int fd) {
  int result = close(fd);
  if (result != 0) {
    Log::D(Log::Tag::TRANSPORT, "close(%d) result=%d (%s)", fd, result,
           strerror(errno));
  }
  return result;
}

}  // namespace profiler
