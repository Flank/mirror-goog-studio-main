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

#ifndef UTILS_LOG_H_
#define UTILS_LOG_H_

#include <cstdarg>

namespace profiler {

// Logging methods that mimic Android's log library. You do not need to add your
// own newlines as these logging methods will do that automatically.
class Log {
 public:
  // Log a message at the verbose level
  static void V(const char *tag, const char *msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Log a message at the debug level
  static void D(const char *tag, const char *msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Log a message at the info level
  static void I(const char *tag, const char *msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Log a message at the warning level
  static void W(const char *tag, const char *msg, ...)
      __attribute__((format(printf, 2, 3)));

  // Log a message at the error level
  static void E(const char *tag, const char *msg, ...)
      __attribute__((format(printf, 2, 3)));

  struct Tag {
    static constexpr const char *const TRANSPORT = "StudioTransport";
    static constexpr const char *const PROFILER = "StudioProfiler";
    static constexpr const char *const APPINSPECT = "AppInspection";
    static constexpr const char *const DDMLIB = "Ddmlib";
    static constexpr const char *const COROUTINE_DEBUGGER = "CoroutineDebugger";
  };
};

}  // namespace profiler

#endif  // UTILS_LOG_H_
