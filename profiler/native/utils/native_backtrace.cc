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
#include <unwind.h>

using std::vector;
using std::uintptr_t;

namespace profiler {

struct BacktraceContext {
  // vector to put unwind addresses to
  vector<uintptr_t> backtrace;
  // how many frames should be put into backtrace at most
  size_t max_frames;
  // how many inner frames should be skipped
  int frames_to_skip;
};

_Unwind_Reason_Code UnwindCallback(_Unwind_Context* unwind_ctx, void* arg) {
  BacktraceContext *backtrace_ctx = static_cast<BacktraceContext *>(arg);
  std::uintptr_t ip = _Unwind_GetIP(unwind_ctx);
  if (ip == 0) {
    return _URC_NO_REASON;
  }

  if (backtrace_ctx->frames_to_skip > 0) {
    // Still skipping frames, no need to add IP to backtrace.
    backtrace_ctx->frames_to_skip--;
    return _URC_NO_REASON;
  }

  if (backtrace_ctx->backtrace.size() < backtrace_ctx->max_frames) {
    // Adding IP to backtrace, because we skipped all the frames
    // we needed to skip and hasn't reached maximum number of frames yet.
    backtrace_ctx->backtrace.push_back(ip);
    return _URC_NO_REASON;
  } else {
    // Already got enough frames, no need to go further.
    return _URC_END_OF_STACK;
  }
}

vector<uintptr_t> GetBacktrace(int max_frames) {
  BacktraceContext backtrace_context;
  backtrace_context.max_frames = static_cast<size_t>(max_frames);
  // One frame for profiler::backtrace itself needs to be skipped.
  backtrace_context.frames_to_skip = 1;
  // Reserving space for requested number of frames.
  backtrace_context.backtrace.reserve(max_frames);

  // Here we use unwinder from C++ ABI support lib to avoid bringing
  // in external dependencies like libunwind and libunwind_llvm.
  // In a little while NDK should provide its own official unwinder.
  _Unwind_Backtrace(UnwindCallback, &backtrace_context);

  // Manually moving backtrace vector, because it is inside of the
  // struct and compiler might not do it for us.
  return std::move(backtrace_context.backtrace);
}

}  // namespace profiler
