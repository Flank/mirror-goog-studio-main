/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.agent.appinspection

import com.android.tools.agent.appinspection.testutils.FrameworkStateRule
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.appinspection.testutils.inspection.InspectorRule
import com.google.common.truth.Truth.assertThat
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue

class ViewLayoutInspectorTest {

    @get:Rule
    val mainLooperRule = MainLooperRule()

    @get:Rule
    val inspectorRule = InspectorRule()

    @get:Rule
    val frameworkRule = FrameworkStateRule()

    @Test
    fun canStartAndStopInspector() = createViewInspector { viewInspector ->

        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.offer(bytes)
        }

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
        }

        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.STOP_FETCH_RESPONSE)
        }
    }

    private fun createViewInspector(block: (ViewLayoutInspector) -> Unit) {
        // We could just create the view inspector directly, but using the factory mimics what
        // actually happens in production.
        val factory = ViewLayoutInspectorFactory()
        val viewInspector =
            factory.createInspector(inspectorRule.connection, inspectorRule.environment)
        block(viewInspector)
        viewInspector.onDispose()
    }
}
