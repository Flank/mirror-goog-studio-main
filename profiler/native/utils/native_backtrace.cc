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
 *
 */
#include "native_backtrace.h"

#include <algorithm>
#define UNW_LOCAL_ONLY
#include <libunwind.h>

namespace profiler {

std::vector<std::uintptr_t> backtrace(int max_frames) {
  std::vector<std::uintptr_t> result;
  result.reserve(std::min(max_frames, 50));
  unw_cursor_t cursor;
  unw_context_t context;
  unw_word_t ip;
  static_assert(sizeof(std::uintptr_t) == sizeof(unw_word_t),
                "libunwind word is not the same size as a pointer.");

  unw_getcontext(&context);
  unw_init_local(&cursor, &context);
  while (max_frames > 0 && unw_step(&cursor) > 0) {
    unw_get_reg(&cursor, UNW_REG_IP, &ip);
    result.push_back(reinterpret_cast<std::uintptr_t>(ip));
    --max_frames;
  }

  return result;
}

}  // namespace profiler
