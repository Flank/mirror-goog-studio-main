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
 */
#ifndef MEMORY_STATS_LOGGER_H
#define MEMORY_STATS_LOGGER_H

#include <unistd.h>
#include <cstdint>

#include "proto/internal_memory.grpc.pb.h"

namespace profiler {

// Queues allocation stats to be sent to perfd.
void EnqueueAllocStats(int32_t alloc_count, int32_t free_count);

// Queues garbage collection stats to be sent to perfd.
// TODO: add count+bytes freed information.
void EnqueueGcStats(int64_t start_time, int64_t end_time);

// Queues the BatchAlllocationSample to be sent to perfd.
// Note the non-const request, as pid is set on the request before the queue.
void EnqueueAllocationEvents(proto::BatchAllocationSample& request);

}  // end of namespace profiler

#endif  // MEMORY_STATS_LOGGER_H
