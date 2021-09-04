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

package com.android.tools.agent.appinspection.nojarjar

import android.content.Context
import android.content.res.Resources
import android.graphics.Picture
import android.os.Looper
import android.view.View
import android.view.WindowManagerGlobal
import checkNonProgressEvent
import com.android.tools.agent.appinspection.ViewLayoutInspector
import com.android.tools.agent.appinspection.ViewLayoutInspectorFactory
import com.android.tools.agent.appinspection.testutils.FrameworkStateRule
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.appinspection.testutils.inspection.InspectorRule
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.google.common.truth.Truth.assertThat
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event.SpecializedCase
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response.SpecializedCase.START_FETCH_RESPONSE
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class ViewLayoutInspectorWithoutAndroidxWindowTest {
    @get:Rule
    val mainLooperRule = MainLooperRule()

    @get:Rule
    val inspectorRule = InspectorRule()

    @Before
    fun setUp() {
        try {
            Class.forName("androidx.window.layout.WindowInfoRepository")
            Assume.assumeTrue(
                "This test needs to be run without WindowInfoRepository on the classpath", false)
        }
        catch (expected: ClassNotFoundException) {}
    }

    @After
    fun tearDown() {
        WindowManagerGlobal.getInstance().rootViews.clear()
    }

    // Verify that we can still run the inspector without the android.window:window dependency
    // used to get folding information
    @Test
    fun inspectorWorksWithoutAndroidxWindow() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val context = Context(packageName, Resources(mapOf<Int, String>()))
        val root = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val fakePicture = Picture(byteArrayOf(1, 2, 3))
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        val updateScreenshotTypeCommand = LayoutInspectorViewProtocol.Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = LayoutInspectorViewProtocol.Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = LayoutInspectorViewProtocol.Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(UPDATE_SCREENSHOT_TYPE_RESPONSE)
        }

        val startFetchCommand = LayoutInspectorViewProtocol.Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = false
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = LayoutInspectorViewProtocol.Response.parseFrom(bytes)
            assertThat(response.specializedCase)
                .isEqualTo(START_FETCH_RESPONSE)
        }

        // In tests, invalidating a view does nothing. We need to trigger the capture manually.
        root.forcePictureCapture(fakePicture)
        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(root.uniqueDrawingId)
        }

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(SpecializedCase.LAYOUT_EVENT)
        }
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(SpecializedCase.PROPERTIES_EVENT)
        }
    }

    private fun createViewInspector(block: (ViewLayoutInspector) -> Unit) {
        // We could just create the view inspector directly, but using the factory mimics what
        // actually happens in production.
        val factory = ViewLayoutInspectorFactory()
        val viewInspector =
            factory.createInspector(inspectorRule.connection, inspectorRule.environment)

        // Save away the threads that are running before the test, so we can check there are no
        // extras after.
        val initialThreads = Looper.getLoopers().keys.toSet()

        block(viewInspector)
        viewInspector.onDispose()

        Looper.getLoopers().keys
            .filter { !initialThreads.contains(it) }
            .forEach { thread -> thread.join(TimeUnit.SECONDS.toMillis(1)) }
    }
}
