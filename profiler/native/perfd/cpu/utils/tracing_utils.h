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

#ifndef PERFD_CPU_UTILS_TRACING_UTILS_H_
#define PERFD_CPU_UTILS_TRACING_UTILS_H_

#include <stdint.h>

namespace profiler {
class TracingUtils {
 public:
  // Check if tracer is running. This is done by reading the
  // pipe directly from the OS and returning true for 1 and false for 0
  static bool IsTracerRunning();
  // Grab the tracing buffer size from the tracer pipe (buffer_size_kb).
  // -1 is returned if the read fails.
  static int GetTracingBufferSize();

 private:
  // Helper function to read int values from Atrace files. This function will
  // enumerate all |files| returning the first int value read from a file. The
  // array is expected to contain the paths to the config files for current and
  // past versions of android.
  static int ReadIntFromConfigFile(const char *files[], uint32_t count);
};
}  // namespace profiler

#endif  // PERFD_CPU_UTILS_TRACING_UTILS_H_
