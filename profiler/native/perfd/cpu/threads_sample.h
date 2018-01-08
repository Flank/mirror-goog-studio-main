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
#ifndef PERFD_CPU_THREADS_SAMPLE_H_
#define PERFD_CPU_THREADS_SAMPLE_H_

#include <vector>

#include "proto/common.pb.h"
#include "proto/cpu.grpc.pb.h"
#include "proto/cpu.pb.h"

namespace profiler {

// Thread-related information captured by a sample of
// /proc/[PID]/task/[TID]/stat files, including the activities (change of a
// thread's state compared to a previous sample) and a snapshot of all alive
// threads' states.
struct ThreadsSample {
  struct Activity {
    int32_t tid;                                       // Thread id
    std::string name;                                  // Thread name
    profiler::proto::GetThreadsResponse::State state;  // Thread state
    int64_t timestamp;                                 // Activity timestamp
  };

  // State of each alive thread in this sample
  profiler::proto::GetThreadsResponse::ThreadSnapshot snapshot;
  // All the activities that were detected by the sample
  std::vector<Activity> activities;
};

}  // namespace profiler

#endif  // PERFD_CPU_THREADS_SAMPLE_H_
