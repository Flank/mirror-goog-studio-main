/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.agent.layoutinspector

import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.android.tools.transport.AgentRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

class LayoutInspectorServiceTest {
    @get:Rule
    var agentRule = AgentRule()

    @Test
    @Throws(Exception::class)
    fun testError() {
        LayoutInspectorService.sendErrorMessage("foo")
        val event = agentRule.events.poll(5, TimeUnit.SECONDS)
        val expected = Common.Event.newBuilder()
            // TODO: use ProcessHandle.current().pid() below once we've switched to jdk11
            .setPid(ManagementFactory.getRuntimeMXBean().name.substringBefore('@').toInt())
            .setGroupId(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR.number.toLong())
            .setKind(Common.Event.Kind.LAYOUT_INSPECTOR)
            .setLayoutInspectorEvent(LayoutInspectorProto.LayoutInspectorEvent.newBuilder().setErrorMessage("foo"))
            .build()
        assertThat(event).isEqualTo(expected)
    }

    private companion object {
        init {
            System.loadLibrary("jni-test")
        }
    }
}