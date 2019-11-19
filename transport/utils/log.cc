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
#include "log.h"

#include <cstdio>

namespace {
void Handle(const char *tag, const char level, const char *fmt, va_list args) {
  printf("%s[%c]: ", tag, level);
  vprintf(fmt, args);
  printf("\n");
  // If debuging threading issues with test uncomment line below
  // to force log lines to flush immediatly.
  // Note: This may impact chance of threading issue to occur however
  // you will have log statements printed as they get called.
  // fflush(stdout);
}
}  // namespace

namespace profiler {

void Log::V(const char *tag, const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  Handle(tag, 'V', fmt, args);
  va_end(args);
}

void Log::D(const char *tag, const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  Handle(tag, 'D', fmt, args);
  va_end(args);
}

void Log::I(const char *tag, const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  Handle(tag, 'I', fmt, args);
  va_end(args);
}

void Log::W(const char *tag, const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  Handle(tag, 'W', fmt, args);
  va_end(args);
}

void Log::E(const char *tag, const char *fmt, ...) {
  va_list args;
  va_start(args, fmt);
  Handle(tag, 'E', fmt, args);
  va_end(args);
}

}  // namespace profiler
