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

package com.android.tools.profiler.energy;

import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyServiceGrpc.EnergyServiceBlockingStub;

/** Wrapper of stub calls to Energy Profiler service. */
public class EnergyStubWrapper {
    private final EnergyServiceBlockingStub myEnergyStub;

    EnergyStubWrapper(EnergyServiceBlockingStub energyStub) {
        myEnergyStub = energyStub;
    }

    EnergyProfiler.EnergyEventsResponse getAllEnergyEvents(Session session) {
        return myEnergyStub.getEvents(
                EnergyProfiler.EnergyRequest.newBuilder()
                        .setSession(session)
                        .setStartTimestamp(0L)
                        .setEndTimestamp(Long.MAX_VALUE)
                        .build());
    }
}
