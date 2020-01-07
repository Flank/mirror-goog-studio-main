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
package com.android.tools.profiler

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profiler.proto.ProfilerServiceGrpc
import com.android.tools.transport.TransportRule
import com.android.tools.transport.device.SdkLevel
import com.android.tools.transport.device.supportsJvmti
import com.google.common.truth.Truth.assertThat
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Wraps [TransportRule] with additional profiler-specific setup. */
class ProfilerRule @JvmOverloads constructor(
        activityClass: String,
        sdkLevel: SdkLevel,
        ruleConfig: ProfilerConfig = ProfilerConfig())
    : ExternalResource() {

    val transportRule = TransportRule(activityClass, sdkLevel, ruleConfig)

    lateinit var session: Common.Session
        private set

    override fun apply(base: Statement, description: Description): Statement {
        return RuleChain.outerRule(transportRule).apply(super.apply(base, description), description)
    }

    override fun before() {
        val requestBuilder = Profiler.BeginSessionRequest.newBuilder().setDeviceId(TransportRule.DUMMY_DEVICE_ID).setPid(transportRule.pid)
        val profilerStub = ProfilerServiceGrpc.newBlockingStub(transportRule.grpc.channel)
        session = profilerStub.beginSession(requestBuilder.build()).session

        if (transportRule.sdkLevel.supportsJvmti()) {
            // The following message is only printed by the JVMTI agent, and we should wait for it
            // before continuing. Pre-jvmti, the code is already transformed and ready to go.
            assertThat(transportRule.androidDriver.waitForInput("Profiler initialization complete on agent.")).isTrue()
        }
    }

    override fun after() {
        val profilerStub = ProfilerServiceGrpc.newBlockingStub(transportRule.grpc.channel)
        profilerStub.endSession(Profiler.EndSessionRequest.newBuilder().setSessionId(session.sessionId).build())
    }
}