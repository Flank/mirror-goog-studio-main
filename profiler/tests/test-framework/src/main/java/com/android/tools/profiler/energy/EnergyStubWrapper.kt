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
package com.android.tools.profiler.energy

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse
import com.android.tools.profiler.proto.EnergyServiceGrpc
import com.android.tools.profiler.proto.EnergyServiceGrpc.EnergyServiceBlockingStub
import com.android.tools.transport.grpc.Grpc

/** Wrapper of stub calls to Energy Profiler service.  */
class EnergyStubWrapper(val energyStub: EnergyServiceBlockingStub) {
    companion object {
        /**
         * Convenience method for creating a wrapper when you don't need to create the underlying
         * stub yourself.
         */
        @JvmStatic
        fun create(grpc: Grpc): EnergyStubWrapper {
            return EnergyStubWrapper(EnergyServiceGrpc.newBlockingStub(grpc.channel))
        }
    }

    fun getAllEnergyEvents(session: Common.Session): EnergyEventsResponse {
        return energyStub.getEvents(
                EnergyProfiler.EnergyRequest.newBuilder()
                        .setSession(session)
                        .setStartTimestamp(0L)
                        .setEndTimestamp(Long.MAX_VALUE)
                        .build())
    }
}