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

@file:JvmName("MemoryTestUtils")

package com.android.tools.profiler.memory

import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.BatchAllocationContexts
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth.assertThat

fun BatchAllocationContexts.findClassTag(className: String): Int {
    for (classAlloc in classesList) {
        if (classAlloc.className.contains(className)) {
            return classAlloc.classId
        }
    }
    return 0
}

fun MemoryRule.startAllocationTracking() {
    // Start memory tracking.
    val transportStub = TransportServiceGrpc.newBlockingStub(transportRule.grpc.channel)
    transportStub.execute(
            Transport.ExecuteRequest.newBuilder()
                    .setCommand(Commands.Command.newBuilder()
                            .setType(Commands.Command.CommandType.START_ALLOC_TRACKING)
                            .setPid(transportRule.pid)
                            .setStartAllocTracking(
                                    Memory.StartAllocTracking.newBuilder().setRequestTime(1)))
                    .build())

    // Ensure the initialization process is finished.
    assertThat(transportRule.androidDriver.waitForInput("Tracking initialization")).isTrue()
}
