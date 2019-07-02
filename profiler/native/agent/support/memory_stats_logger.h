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

#include "proto/memory_data.grpc.pb.h"
#include "proto/transport.grpc.pb.h"

namespace profiler {

// Queues allocation stats to be sent to perfd.
void EnqueueAllocStats(int32_t alloc_count, int32_t free_count);

// Queues garbage collection stats to be sent to perfd.
// TODO: add count+bytes freed information.
void EnqueueGcStats(int64_t start_time, int64_t end_time);

// Generates and queues the new-pipelne MEMORY_ALLOC_TRACKING and
// MEMORY_ALLOC_TRACKING_STATUS events to be sent to the daemon.
// |track_start_timestamp| is the time when the current allocation
// tracking capture was first enabled, which is the id used for grouping
// the start and end tracking event.
// |command_success| indicates whether the start/stop request was successful.
// e.g. the start request could fail if tracking is already in progress, in
// which case no new AllocationsInfo will be generated.
void EnqueueAllocationInfoEvents(const proto::Command& command,
                                 int64_t track_start_timestamp,
                                 bool command_success);

// Queues the BatchAllocationContexts and BatchAllocationEvents to be sent to
// perfd.
void EnqueueAllocationEvents(const proto::BatchAllocationContexts& contexts,
                             const proto::BatchAllocationEvents& events);

// Queues the BatchAllocationContexts and BatchJNIGlobalRefEvent to be sent to
// perfd.
void EnqueueJNIGlobalRefEvents(const proto::BatchAllocationContexts& contexts,
                               const proto::BatchJNIGlobalRefEvent& events);

// Queues the AllocationSamplingRateEvent to be sent to perfd.
void EnqueueAllocationSamplingRateEvent(int64_t timestamp,
                                        int32_t sampling_num_interval);

}  // end of namespace profiler

#endif  // MEMORY_STATS_LOGGER_H
