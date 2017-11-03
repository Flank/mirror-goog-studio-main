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
#ifndef UTILS_NATIVE_BACKTRACE_H_
#define UTILS_NATIVE_BACKTRACE_H_
#include <cstdint>
#include <vector>

namespace profiler {

// Returns a vector of program counters obtained by walking the stack
// of a current thread starting from the profiler::backtrace
// invocation point.
// Addresses are ordered from innermost to outermost frames.
// Size of the returned vector is limited by the max_frames parameter.
std::vector<std::uintptr_t> backtrace(int max_frames);

}
#endif  // UTILS_NATIVE_BACKTRACE_H_
