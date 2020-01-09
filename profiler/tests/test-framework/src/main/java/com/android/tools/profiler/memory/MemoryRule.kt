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
package com.android.tools.profiler.memory

import com.android.tools.profiler.ProfilerConfig
import com.android.tools.profiler.ProfilerRule
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest
import com.android.tools.profiler.proto.MemoryServiceGrpc
import com.android.tools.transport.TransportRule
import com.android.tools.transport.device.SdkLevel
import io.grpc.StatusRuntimeException
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Wraps [TransportRule] with memory-specific setup and teardown steps.  */
class MemoryRule @JvmOverloads constructor(
        activityClass: String,
        sdkLevel: SdkLevel,
        ruleConfig: ProfilerConfig = ProfilerConfig())
    : ExternalResource() {

    val profilerRule = ProfilerRule(activityClass, sdkLevel, ruleConfig)
    val transportRule = profilerRule.transportRule

    override fun apply(base: Statement, description: Description): Statement {
        return RuleChain.outerRule(profilerRule).apply(super.apply(base, description), description)
    }

    override fun before() {
        // For Memory tests, we need to invoke beginSession and startMonitoringApp to properly
        // initialize the memory cache and establish the perfa->perfd connection
        val memoryStub = MemoryServiceGrpc.newBlockingStub(transportRule.grpc.channel)
        memoryStub.startMonitoringApp(MemoryStartRequest.newBuilder().setSession(profilerRule.session).build())
    }

    override fun after() {
        try {
            val memoryStub = MemoryServiceGrpc.newBlockingStub(transportRule.grpc.channel)
            memoryStub.stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(profilerRule.session).build())
        } catch (e: StatusRuntimeException) { // TODO(b/112274301): fix "connection closed" error.
        }
        super.after()
    }
}