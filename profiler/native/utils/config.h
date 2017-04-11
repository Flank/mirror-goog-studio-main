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
#ifndef UTILS_CONFIG_H_
#define UTILS_CONFIG_H_

namespace profiler {

// This is a Unix abstract socket name that designates an abstract socket of
// name "AndroidStudioProfiler" (removing the "@" prefix).
const char* const kDaemonSocketName = "@AndroidStudioProfiler";

// This is a Unix abstract socket name that is passed to bind() with the
// '@' replaced by '\0'. It designates an abstract socket of name
// "AndroidStudioProfilerAgent" (removing the "@" prefix).
const char* const kAgentSocketName = "@AndroidStudioProfilerAgent";

// Address used for legacy devices (Nougat or older).
const char* const kServerAddress = "127.0.0.1:12389";

}  // namespace profiler

#endif  // UTILS_CONFIG_H_
