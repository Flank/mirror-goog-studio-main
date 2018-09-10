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

import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.MemoryProfiler.AllocationSamplingRate;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateRequest;
import com.android.tools.profiler.proto.MemoryProfiler.SetAllocationSamplingRateResponse;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;

/** Wrapper of stub calls that is shared among tests. */
public final class MemoryStubWrapper {
    private final MemoryServiceBlockingStub myMemoryStub;

    public MemoryStubWrapper(MemoryServiceBlockingStub memoryStub) {
        myMemoryStub = memoryStub;
    }

    MemoryData getJvmtiData(Session session, long startTime, long endTime) {
        return myMemoryStub.getJvmtiData(
                MemoryRequest.newBuilder()
                        .setSession(session)
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build());
    }

    MemoryData getMemoryData(Session session, long startTime, long endTime) {
        return myMemoryStub.getData(
                MemoryRequest.newBuilder()
                        .setSession(session)
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build());
    }

    public TrackAllocationsResponse startAllocationTracking(Session session) {
        return myMemoryStub.trackAllocations(
                TrackAllocationsRequest.newBuilder().setSession(session).setEnabled(true).build());
    }

    SetAllocationSamplingRateResponse setSamplingRate(Session session, int samplingNumInterval) {
        return myMemoryStub.setAllocationSamplingRate(
                SetAllocationSamplingRateRequest.newBuilder()
                        .setSession(session)
                        .setSamplingRate(
                                AllocationSamplingRate.newBuilder()
                                        .setSamplingNumInterval(samplingNumInterval))
                        .build());
    }
}
