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

#include "trace.h"

#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#include <iostream>

namespace deployer {

int Trace::trace_marker_fd;

Trace::Trace(const char* name) { Begin(name); }

Trace::Trace(const std::string& name) { Begin(name.c_str()); }

Trace::~Trace() { End(); }

#if defined(__ANDROID__)
void Trace::Init() {
  std::cout << "Tracing is enabled" << std::endl;
  if (trace_marker_fd == 0) {
    trace_marker_fd = open("/sys/kernel/debug/tracing/trace_marker", O_WRONLY);
    // TODO: Error handling
    if (trace_marker_fd == -1) {
    }
  }
}
#else
void Trace::Init() { std::cout << "Tracing is disabled" << std::endl; }
#endif

}  // namespace deployer