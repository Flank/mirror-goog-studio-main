/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Transport;

import static com.google.common.truth.Truth.assertThat;

public class UnifiedPipelineMemoryTestUtils {

    // Just a class for static helpers
    private UnifiedPipelineMemoryTestUtils() {
    }

    public static int findClassTag(Memory.BatchAllocationContexts sample, String className) {
        for (Memory.AllocatedClass classAlloc : sample.getClassesList()) {
            if (classAlloc.getClassName().contains(className)) {
                return classAlloc.getClassId();
            }
        }
        return 0;
    }

    public static void startAllocationTracking(PerfDriver perfDriver) {
        // Start memory tracking.
        perfDriver.getGrpc().getTransportStub().execute(
                Transport.ExecuteRequest.newBuilder()
                        .setCommand(Commands.Command.newBuilder()
                                .setType(Commands.Command.CommandType.START_ALLOC_TRACKING)
                                .setPid(perfDriver.getSession().getPid())
                                .setStartAllocTracking(Memory.StartAllocTracking
                                        .newBuilder().setRequestTime(1)))
                        .build());
        // Ensure the initialization process is finished.
        assertThat(perfDriver.getFakeAndroidDriver()
                .waitForInput("Tracking initialization")).isTrue();
    }

}
