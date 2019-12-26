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
package com.android.tools.profiler.memory

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.MemoryProfiler.*
import com.android.tools.profiler.proto.MemoryServiceGrpc
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub
import com.android.tools.transport.grpc.Grpc

/** Wrapper of stub calls that is shared among tests.  */
class MemoryStubWrapper(val memoryStub: MemoryServiceBlockingStub) {
    companion object {
        /**
         * Convenience method for creating a wrapper when you don't need to create the underlying
         * stub yourself.
         */
        @JvmStatic
        fun create(grpc: Grpc): MemoryStubWrapper {
            return MemoryStubWrapper(MemoryServiceGrpc.newBlockingStub(grpc.channel))
        }
    }

    fun getJvmtiData(session: Common.Session, startTime: Long, endTime: Long): MemoryData {
        return memoryStub.getJvmtiData(
                MemoryRequest.newBuilder()
                        .setSession(session)
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build())
    }

    fun getMemoryData(session: Common.Session?, startTime: Long, endTime: Long): MemoryData {
        return memoryStub.getData(
                MemoryRequest.newBuilder()
                        .setSession(session)
                        .setStartTime(startTime)
                        .setEndTime(endTime)
                        .build())
    }

    fun startAllocationTracking(session: Common.Session?): TrackAllocationsResponse {
        return memoryStub.trackAllocations(
                TrackAllocationsRequest.newBuilder().setSession(session).setEnabled(true).build())
    }

    fun setSamplingRate(session: Common.Session?, samplingNumInterval: Int): SetAllocationSamplingRateResponse {
        return memoryStub.setAllocationSamplingRate(
                SetAllocationSamplingRateRequest.newBuilder()
                        .setSession(session)
                        .setSamplingRate(
                                Memory.MemoryAllocSamplingData.newBuilder()
                                        .setSamplingNumInterval(samplingNumInterval))
                        .build())
    }

}