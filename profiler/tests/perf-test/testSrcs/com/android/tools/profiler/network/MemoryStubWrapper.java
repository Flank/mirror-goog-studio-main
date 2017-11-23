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

package com.android.tools.profiler.memory;

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;

/** Wrapper of stub calls that is shared among tests. */
final class MemoryStubWrapper {
    private static final int SLEEP_TIME_MS = 200;
    private final MemoryServiceBlockingStub myMemoryStub;

    MemoryStubWrapper(MemoryServiceBlockingStub memoryStub) {
        myMemoryStub = memoryStub;
    }

    MemoryProfiler.MemoryData getJvmtiData(int processId, long startTime, long endTime) {
        return myMemoryStub.getJvmtiData(
                MemoryProfiler.MemoryRequest.newBuilder()
                        .setProcessId(processId)
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build());
    }

    MemoryProfiler.TrackAllocationsResponse trackAllocations(int processId) {
        return myMemoryStub.trackAllocations(
                MemoryProfiler.TrackAllocationsRequest.newBuilder()
                        .setProcessId(processId)
                        .build());
    }

    MemoryProfiler.TrackAllocationsResponse startAllocationTracking(int processId) {
        myMemoryStub.startMonitoringApp(
                MemoryProfiler.MemoryStartRequest.newBuilder().setProcessId(processId).build());

        return myMemoryStub.trackAllocations(
                MemoryProfiler.TrackAllocationsRequest.newBuilder()
                        .setProcessId(processId)
                        .setEnabled(true)
                        .build());
    }
}
