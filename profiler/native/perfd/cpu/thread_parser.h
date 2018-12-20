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
#ifndef PERFD_CPU_THREAD_PARSER_H_
#define PERFD_CPU_THREAD_PARSER_H_

#include <string>
#include <vector>

#include "proto/cpu_data.pb.h"
#include "utils/procfs_files.h"

namespace profiler {

// Gets thread IDs under a given process of |pid|. Returns true on success.
bool GetThreads(const ProcfsFiles* procfs, int32_t pid,
                std::vector<int32_t>* tids);

// Gets |state| and |name| of a given thread of |tid| under process of |pid|.
// Returns true on success.
bool GetThreadState(const ProcfsFiles* procfs, int32_t pid, int32_t tid,
                    profiler::proto::CpuThreadData::State* state,
                    std::string* name);

// Parses a thread's stat file (proc/[pid]/task/[tid]/stat). If success,
// returns true and saves extracted info into |state| and |name|.
//
// For a thread, the following fields are read (the first field is numbered as
// 1).
//    (1) id  %d                      => For sanity checking.
//    (2) comm  %s (in parentheses)   => Output |name|.
//    (3) state  %c                   => Output |state|.
// See more details at http://man7.org/linux/man-pages/man5/proc.5.html.
bool ParseThreadStat(int32_t tid, const std::string& content, char* state,
                     std::string* name);

// Converts a thread state from character type to an enum type.
// According to http://man7.org/linux/man-pages/man5/proc.5.html, 'W' could mean
// Paging (only before Linux 2.6.0) or Waking (Linux 2.6.33 to 3.13 only).
// Android 1.0 already used kernel 2.6.25.
profiler::proto::CpuThreadData::State ThreadStateInEnum(char state_in_char);

}  // namespace profiler

#endif  // PERFD_CPU_THREAD_PARSER_H_