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

#ifndef TRACE_H
#define TRACE_H

#include <unistd.h>
#include <string>

namespace deployer {

class Trace {
 public:
  explicit Trace(const char *name);
  explicit Trace(const std::string &name);
  Trace(const Trace &) = delete;
  Trace(Trace &&) = delete;
  ~Trace();
  static void Init();

#if defined(__ANDROID__)
  inline void Begin(const char *name) const noexcept {
    char buf[kTraceMessageLen];
    int len = snprintf(buf, kTraceMessageLen, "B|%d|%s", getpid(), name);
    write(trace_marker_fd, buf, len);
  }

  inline void End() const noexcept {
    char c = 'E';
    write(trace_marker_fd, &c, 1);
  }
#else
  void Begin(const char *name) const noexcept {}
  void End() const noexcept {}
#endif

 private:
  static int trace_marker_fd;
  static const size_t kTraceMessageLen = 256;
};

}  // namespace deployer
#endif  // UTILS_TRACE_H