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
package com.android.tools.profiler

import com.android.tools.profiler.proto.Agent.AgentConfig
import com.android.tools.profiler.proto.Agent.AgentConfig.MemoryConfig
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData
import com.android.tools.transport.TransportRule

/**
 * Additional profiler configurations that can be passed into a [TransportRule].
 */
open class ProfilerConfig : TransportRule.Config() {
    open fun usesUnifiedPipeline() = false
    open val liveAllocSampleRate = 1

    final override fun initDaemonConfig(daemonConfig: Common.CommonConfig.Builder) {
        daemonConfig.profilerUnifiedPipeline = usesUnifiedPipeline()
        daemonConfig.energyProfilerEnabled = true
    }

    final override fun initAgentConfig(agentConfig: AgentConfig.Builder) {
        val memConfig = MemoryConfig.newBuilder()
                .setTrackGlobalJniRefs(true)
                .setAppDir("/")
                .setMaxStackDepth(50)
                .setSamplingRate(
                        MemoryAllocSamplingData.newBuilder()
                                .setSamplingNumInterval(liveAllocSampleRate)
                                .build())
                .build()

        agentConfig.mem = memConfig
    }

    final override fun onBeforeActivityLaunched(transportRule: TransportRule) {
        transportRule.androidDriver.setProperty("profiler.service.address", transportRule.commonConfig.serviceAddress)
    }
}
