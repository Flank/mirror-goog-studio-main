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

import android.content.Context
import android.content.res.Resources
import android.graphics.Picture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Build
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewRootImpl
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import checkNextEventMatching
import com.android.testutils.PropertySetterRule
import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.testutils.FrameworkStateRule
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.appinspection.testutils.inspection.InspectorRule
import com.android.tools.agent.appinspection.testutils.property.companions.AppCompatButtonInspectionCompanion
import com.android.tools.agent.appinspection.testutils.property.companions.ButtonInspectionCompanion
import com.android.tools.agent.appinspection.testutils.property.companions.TextViewInspectionCompanion
import com.android.tools.agent.appinspection.testutils.property.companions.ViewGroupLayoutParamsInspectionCompanion
import com.android.tools.agent.appinspection.testutils.property.companions.ViewInspectionCompanion
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.agent.appinspection.util.decompress
import com.android.tools.agent.shared.FoldObserver
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.layoutinspector.BITMAP_HEADER_SIZE
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.toBytes
import com.google.common.truth.Truth.assertThat
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorCode
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.FoldEvent.FoldState
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

class ViewLayoutInspectorTestWithWorkaround : ViewLayoutInspectorTestBase() {
    @get:Rule
    val apiLevelRule = PropertySetterRule(31, Build.VERSION::SDK_INT)
}

class ViewLayoutInspectorTest : ViewLayoutInspectorTestBase() {
    @get:Rule
    val apiLevelRule = PropertySetterRule(33, Build.VERSION::SDK_INT)
}

abstract class ViewLayoutInspectorTestBase {

    @get:Rule
    val mainLooperRule = MainLooperRule()

    @get:Rule
    val inspectorRule = InspectorRule()

    @get:Rule
    val frameworkRule = FrameworkStateRule()

    private val threadExceptions = mutableListOf<Throwable>()

    @Before
    fun trackThread() {
        ThreadUtils.registerThread = {
            it.uncaughtExceptionHandler =
                Thread.UncaughtExceptionHandler { _, ex -> threadExceptions.add(ex) }
        }
    }

    @After
    fun reportThreadDeaths() {
        ThreadUtils.registerThread = {}
        try {
            threadExceptions.forEach { throw it }
        }
        finally {
            threadExceptions.clear()
        }
    }

    @Test
    fun canStartAndStopInspector() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val root = ViewGroup(Context(packageName, createResources(packageName))).apply {
            width = 100
            height = 200
            setAttachInfo(View.AttachInfo())
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

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
            assertThat(response.startFetchResponse.error).isEmpty()
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

    @Test
    fun canStartWithNoWindowInitially() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(5)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        var checkCount = 0
        val packageName = "view.inspector.test"
        val root = object: View(Context(packageName, createResources(packageName))) {
            override fun isAttachedToWindow(): Boolean {
                // For the first few tries say this window isn't attached and thus not a valid root
                return checkCount++ > 5
            }
        }.apply {
            setAttachInfo(View.AttachInfo())
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))
        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = false
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
            assertThat(response.startFetchResponse.error).isEmpty()
        }
        ThreadUtils.runOnMainThread { }.get()

        // If we didn't get any roots initially the view callback won't be registered and we won't
        // get a response here.
        root.forcePictureCapture(Picture(byteArrayOf(1)))
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }
    }

    @Test
    fun foldEventsSent() = createViewInspector { viewInspector ->
        val myObserver = object : FoldObserver {
            override var foldState: FoldState? =
                FoldState.HALF_OPEN
            override val orientation: LayoutInspectorViewProtocol.FoldEvent.FoldOrientation =
                LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.HORIZONTAL

            override fun startObservingFoldState(rootView: View) {}
            override fun stopObservingFoldState(rootView: View) {}
            override fun shutdown() {}
        }
        var fetchContinuously = true
        viewInspector.foldSupportOverrideForTests =
            FoldSupport(inspectorRule.connection, { fetchContinuously }, myObserver)

        val eventQueue = ArrayBlockingQueue<ByteArray>(15)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        WindowManagerGlobal.getInstance().rootViews.add(tree1)

        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        val angleSensor = object : Sensor() {
            override fun addListener(listener: SensorEventListener?) {
                super.addListener(listener)
                fire(SensorEvent().apply { values = floatArrayOf(150f) })
            }
        }
        context.sensorManager.addSensor(Sensor.TYPE_HINGE_ANGLE, angleSensor)
        val root = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val fakePicture = Picture(byteArrayOf(1, 2, 3))
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        ThreadUtils.runOnMainThread { }.get()

        root.forcePictureCapture(fakePicture)

        checkNextEventMatching(
            eventQueue, { it.specializedCase == Event.SpecializedCase.FOLD_EVENT}) { event ->
            assertThat(event.foldEvent.angle).isEqualTo(150)
        }

        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(100f) })

        checkNextEventMatching(
            eventQueue, { it.specializedCase == Event.SpecializedCase.FOLD_EVENT}) { event ->
            assertThat(event.foldEvent.angle).isEqualTo(100)
        }

        // Set the state to null. We should get one empty event and then no more.
        myObserver.foldState = null
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(0f) })

        checkNextEventMatching(
            eventQueue, { it.specializedCase == Event.SpecializedCase.FOLD_EVENT}) { event ->
            assertThat(event.foldEvent.foldState).isEqualTo(FoldState.UNKNOWN_FOLD_STATE)
        }
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(1f) })
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(2f) })

        // Set the state back to something, and we should start getting events again.
        myObserver.foldState = FoldState.HALF_OPEN
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(30f) })
        checkNextEventMatching(
            eventQueue, { it.specializedCase == Event.SpecializedCase.FOLD_EVENT}) { event ->
            assertThat(event.foldEvent.angle).isEqualTo(30)
        }

        // Turn off live updates. We should stop getting events.
        fetchContinuously = false
        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        // Fire several sensor events, but there shouldn't be FoldEvents generated.
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(80f) })
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(70f) })
        angleSensor.fire(SensorEvent().apply { values = floatArrayOf(60f) })

        // Now refresh the view: we should only get the latest fold event.
        val refreshCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = false
            }
        }.build()
        viewInspector.onReceiveCommand(
            refreshCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        ThreadUtils.runOnMainThread { }.get()
        tree1.forcePictureCapture(fakePicture)

        checkNextEventMatching(
            eventQueue, { it.specializedCase == Event.SpecializedCase.FOLD_EVENT}) { event ->
            assertThat(event.foldEvent.angle).isEqualTo(60)
        }
    }

    @Test
    fun restartingInspectorResendsRootEvents() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val root = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val fakePicture = Picture(byteArrayOf(1, 2, 3))
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        // First, we start up the inspector, to ensure that we've gotten to a point where a
        // WindowRootsEvent is sent back to us (so we know that the inspector is keeping track of
        // the state).
        run {
            val updateScreenshotTypeCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.SKP
                }
            }.build()
            viewInspector.onReceiveCommand(
                updateScreenshotTypeCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
            }

            val startFetchCommand = Command.newBuilder().apply {
                startFetchCommandBuilder.apply {
                    // Set continuous to false since we don't need it to be true to clear the root IDs
                    // as a side effect
                    continuous = false
                }
            }.build()
            viewInspector.onReceiveCommand(
                startFetchCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
                assertThat(response.startFetchResponse.error).isEmpty()
            }

            // In tests, invalidating a view does nothing. We need to trigger the capture manually.
            root.forcePictureCapture(fakePicture)
            ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
                assertThat(event.rootsEvent.idsList).containsExactly(root.uniqueDrawingId)
            }

            // Consume additional events that are generated so they don't block the queue
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            }
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            }
        }

        // Next, Stop and restart - this should cause a new roots event to be sent out. Previously,
        // this didn't work because state was left over from the previous run
        run {
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
            val startFetchCommand = Command.newBuilder().apply {
                startFetchCommandBuilder.apply {
                    continuous = false
                }
            }.build()
            viewInspector.onReceiveCommand(
                startFetchCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
                assertThat(response.startFetchResponse.error).isEmpty()
            }
            ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

            root.forcePictureCapture(fakePicture)
            eventQueue.take().let { bytes ->
                val event = Event.parseFrom(bytes)
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
                assertThat(event.rootsEvent.idsList).containsExactly(root.uniqueDrawingId)
            }
        }

    }

    @Test
    fun canCaptureTreeInContinuousMode() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(7)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree2 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree3 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(tree1, tree2))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        val tree1FakePicture1 = Picture(byteArrayOf(1, 1))
        val tree1FakePicture2 = Picture(byteArrayOf(1, 2))
        val tree1FakePicture3 = Picture(byteArrayOf(1, 3))
        val tree1FakePicture4 = Picture(byteArrayOf(1, 4))
        val tree2FakePicture = Picture(byteArrayOf(2))
        val tree3FakePicture = Picture(byteArrayOf(3))

        tree1.forcePictureCapture(tree1FakePicture1)
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId
            )
        }

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture1.bytes)
            }
        }

        tree2.forcePictureCapture(tree2FakePicture)
        // Roots event not resent, as roots haven't changed
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree2.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree2FakePicture.bytes)
            }
        }

        WindowManagerGlobal.getInstance().rootViews.add(tree3)

        tree1.forcePictureCapture(tree1FakePicture2)
        // As a side-effect, this capture discovers the newly added third tree
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture2.bytes)
            }
        }

        // Roots changed - this should generate a new roots event
        WindowManagerGlobal.getInstance().rootViews.remove(tree2)
        tree1.forcePictureCapture(tree1FakePicture3)

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture3.bytes)
            }
        }

        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for the stop command to run its course

        // Normally, stopping the inspector triggers invalidate calls, but in fake android, those
        // do nothing. Instead, we emulate this by manually firing capture events.
        tree1.forcePictureCapture(tree1FakePicture4)
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture4.bytes)

            }
        }
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            assertThat(event.propertiesEvent.rootId).isEqualTo(tree1.uniqueDrawingId)
        }

        tree3.forcePictureCapture(tree3FakePicture)
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree3.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree3FakePicture.bytes)

            }
        }
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            assertThat(event.propertiesEvent.rootId).isEqualTo(tree3.uniqueDrawingId)
        }
    }

    @Test
    fun onlyMostRecentRequestProcessed() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(7)

        val picture1Latch = CountDownLatch(1)
        val picture1Started = CountDownLatch(1)
        val picture2Started = CountDownLatch(1)

        inspectorRule.connection.eventListeners.add { bytes ->
            val event = Event.parseFrom(bytes)
            val pictureBytes = event.layoutEvent?.screenshot?.bytes
            if (pictureBytes?.equals(ByteString.copyFrom(byteArrayOf(1, 1))) == true) {
                picture1Started.countDown()
                picture1Latch.await()
            }
            else if (pictureBytes?.equals(ByteString.copyFrom(byteArrayOf(2, 1))) == true) {
                picture2Started.countDown()
            }
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree2 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(tree1, tree2))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        val tree1FakePicture1 = Picture(byteArrayOf(1, 1))
        val tree1FakePicture2 = Picture(byteArrayOf(1, 2))
        val tree1FakePicture3 = Picture(byteArrayOf(1, 3))
        val tree2FakePicture1 = Picture(byteArrayOf(2, 1))
        val tree2FakePicture2 = Picture(byteArrayOf(2, 2))

        thread {
            // This will block
            tree1.forcePictureCapture(tree1FakePicture1)
            picture1Started.await()

            // This will queue up but should be skipped
            tree1.forcePictureCapture(tree1FakePicture2)
            // This one will be run once the first is unblocked
            tree1.forcePictureCapture(tree1FakePicture3)

            // Different tree--these should both be processed
            tree2.forcePictureCapture(tree2FakePicture1)
            picture2Started.await()
            tree2.forcePictureCapture(tree2FakePicture2)
        }

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId
            )
        }

        // We should get the two events for tree2
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree2.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree2FakePicture1.bytes)
            }
        }
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree2.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree2FakePicture2.bytes)
            }
        }

        // Now continue the initial capture for tree1
        picture1Latch.countDown()

        // We should get the first and third capture for tree1
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture1.bytes)
            }
        }
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture3.bytes)
            }
        }
    }

    @Test
    fun checkRootsThreadIsStartedInContinuousMode() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(3)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo()) }
        val tree2 = View(context).apply { setAttachInfo(View.AttachInfo()) }
        val tree3 = View(context).apply { setAttachInfo(View.AttachInfo()) }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(tree1, tree2))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        // At this point, a check roots thread is running continuously...

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId
            )
        }

        WindowManagerGlobal.getInstance().rootViews.add(tree3)
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        WindowManagerGlobal.getInstance().rootViews.remove(tree2)
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }
    }


    @Test
    fun noEmptyRootsEventOnStopContinuousCapturing() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo()) }
        WindowManagerGlobal.getInstance().rootViews.add(tree1)

        viewInspector.onReceiveCommand(
            Command.newBuilder().apply {
                startFetchCommandBuilder.apply {
                    continuous = true
                }
            }.build().toByteArray(),
            inspectorRule.commandCallback
        )
        // Don't need to get into the screenshot processing stuff
        viewInspector.onReceiveCommand(
            Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.NONE
                }
            }.build().toByteArray(),
            inspectorRule.commandCallback
        )

        // At this point, a check roots thread is running continuously...

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
            )
        }

        val inRootsDetectorLatch = CountDownLatch(1)
        val tree2 = object: View(context) {
            // getVisibility is called in getRootViews, so we introduce a delay to make that request
            // timeout.
            override fun getVisibility(): Int {
                inRootsDetectorLatch.countDown()
                // wait for request in rootsDetector to time out
                Thread.sleep(500)
                // Remove this tree--we don't need to sleep and countDown again.
                WindowManagerGlobal.getInstance().rootViews.remove(this)
                return super.getVisibility()
            }
        }
        WindowManagerGlobal.getInstance().rootViews.add(tree2)
        // Wait until we're in the middle of getting the roots
        inRootsDetectorLatch.await()

        // Switch to non-continuous. This should make the roots detector quit without fetching the
        // roots.
        viewInspector.onReceiveCommand(
            Command.newBuilder().apply {
                startFetchCommandBuilder.apply {
                    continuous = false
                }
            }.build().toByteArray(),
            inspectorRule.commandCallback
        )
        // normally start would trigger a capture, but we have to do that manually here
        tree1.forcePictureCapture(Picture(byteArrayOf(2)))

        // the roots event received should have the right roots in it
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
            )
        }
    }

    @Test
    fun nodeBoundsCapturedAsExpected() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(8)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val mainScreen = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 400
            height = 800
        }
        val floatingDialog = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 300
            height = 200
        }
        val stubPicture = Picture(byteArrayOf(0))

        // Used for root offset
        floatingDialog.locationInSurface.apply {
            x = 10
            y = 20
        }
        // Used for absolution position of dialog root
        floatingDialog.locationOnScreen.apply {
            x = 80
            y = 200
        }

        mainScreen.addView(ViewGroup(context).apply {
            scrollX = 5
            scrollY = 100
            left = 20
            top = 30
            width = 40
            height = 50

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
            })

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
                setTransformedPoints(floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f))
            })
        })

        floatingDialog.addView(ViewGroup(context).apply {
            scrollX = 5
            scrollY = 100
            left = 20
            top = 30
            width = 40
            height = 50

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
            })

            addView(View(context).apply {
                left = 40
                top = 10
                width = 20
                height = 30
                setTransformedPoints(floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f))
            })
        })

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(mainScreen, floatingDialog))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        mainScreen.forcePictureCapture(stubPicture)
        checkNonProgressEvent(eventQueue) { event ->
            // In this test, we don't care that much about this event, but we consume io get to the
            // layout event
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                layoutEvent.rootOffset.let { rootOffset ->
                    assertThat(rootOffset.x).isEqualTo(0)
                    assertThat(rootOffset.y).isEqualTo(0)
                }

                val root = layoutEvent.rootView
                val parent = root.getChildren(0)
                val child0 = parent.getChildren(0)
                val child1 = parent.getChildren(1)

                assertThat(root.id).isEqualTo(mainScreen.uniqueDrawingId)
                root.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(0)
                    assertThat(rect.y).isEqualTo(0)
                    assertThat(rect.w).isEqualTo(400)
                    assertThat(rect.h).isEqualTo(800)
                }
                parent.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(20)
                    assertThat(rect.y).isEqualTo(30)
                    assertThat(rect.w).isEqualTo(40)
                    assertThat(rect.h).isEqualTo(50)
                }
                child0.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(55)
                    assertThat(rect.y).isEqualTo(-60)
                    assertThat(rect.w).isEqualTo(20)
                    assertThat(rect.h).isEqualTo(30)
                }
                child1.bounds.render.let { quad ->
                    assertThat(quad.x0).isEqualTo(10)
                    assertThat(quad.y0).isEqualTo(20)
                    assertThat(quad.x1).isEqualTo(30)
                    assertThat(quad.y1).isEqualTo(40)
                    assertThat(quad.x2).isEqualTo(50)
                    assertThat(quad.y2).isEqualTo(60)
                    assertThat(quad.x3).isEqualTo(70)
                    assertThat(quad.y3).isEqualTo(80)
                }
            }
        }

        floatingDialog.forcePictureCapture(stubPicture)
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                layoutEvent.rootOffset.let { rootOffset ->
                    assertThat(rootOffset.x).isEqualTo(10)
                    assertThat(rootOffset.y).isEqualTo(20)
                }

                val root = layoutEvent.rootView
                val parent = root.getChildren(0)
                val child0 = parent.getChildren(0)
                val child1 = parent.getChildren(1)
                assertThat(root.id).isEqualTo(floatingDialog.uniqueDrawingId)

                root.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(80)
                    assertThat(rect.y).isEqualTo(200)
                    assertThat(rect.w).isEqualTo(300)
                    assertThat(rect.h).isEqualTo(200)
                }
                parent.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(100)
                    assertThat(rect.y).isEqualTo(230)
                    assertThat(rect.w).isEqualTo(40)
                    assertThat(rect.h).isEqualTo(50)
                }
                child0.bounds.layout.let { rect ->
                    assertThat(rect.x).isEqualTo(135)
                    assertThat(rect.y).isEqualTo(140)
                    assertThat(rect.w).isEqualTo(20)
                    assertThat(rect.h).isEqualTo(30)
                }
                child1.bounds.render.let { quad ->
                    assertThat(quad.x0).isEqualTo(10)
                    assertThat(quad.y0).isEqualTo(20)
                    assertThat(quad.x1).isEqualTo(30)
                    assertThat(quad.y1).isEqualTo(40)
                    assertThat(quad.x2).isEqualTo(50)
                    assertThat(quad.y2).isEqualTo(60)
                    assertThat(quad.x3).isEqualTo(70)
                    assertThat(quad.y3).isEqualTo(80)
                }
            }
        }
    }

    @Test
    fun correctBitmapTypesCaptured() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(5)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val mainScreen = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 400
            height = 800
        }
        val floatingDialog = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 300
            height = 200
        }
        val fakeBitmapHeader = byteArrayOf(1, 2, 3) // trailed by 0s
        val floatingFakeBitmapHeader = byteArrayOf(3, 2, 1) // trailed by 0s

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(mainScreen, floatingDialog))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        mainScreen.viewRootImpl = ViewRootImpl()
        mainScreen.viewRootImpl.mSurface = Surface()
        mainScreen.viewRootImpl.mSurface.bitmapBytes = fakeBitmapHeader
        mainScreen.forcePictureCapture(Picture(byteArrayOf(1)))

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }
        val check = { event: Event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)

            event.layoutEvent.screenshot.let { screenshot ->
                assertThat(screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
                val decompressedBytes = screenshot.bytes.toByteArray().decompress()

                // The full screenshot byte array is width * height
                assertThat(decompressedBytes.size).isEqualTo(
                    BITMAP_HEADER_SIZE + mainScreen.width * mainScreen.height
                )
                // Check the bitmap header
                assertThat(decompressedBytes.take(BITMAP_HEADER_SIZE)).isEqualTo(
                    ((mainScreen.width).toBytes() + (mainScreen.height).toBytes() +
                            BitmapType.RGB_565.byteVal).asList()
                )
                // Check the first few bytes to make sure they match our header,
                // that's enough to know that all the data went through correctly.
                assertThat(
                    decompressedBytes
                        .drop(BITMAP_HEADER_SIZE)
                        .take(fakeBitmapHeader.size)
                ).isEqualTo(
                    fakeBitmapHeader.asList()
                )
            }
        }
        checkNonProgressEvent(eventQueue, check)
        // There will be a second one to capture the end of any animation.
        checkNonProgressEvent(eventQueue, check)

        floatingDialog.viewRootImpl = ViewRootImpl()
        floatingDialog.viewRootImpl.mSurface = Surface()
        floatingDialog.viewRootImpl.mSurface.bitmapBytes = floatingFakeBitmapHeader
        floatingDialog.forcePictureCapture(Picture(byteArrayOf(2)))

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)

            event.layoutEvent.screenshot.let { screenshot ->
                assertThat(screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
                val decompressedBytes = screenshot.bytes.toByteArray().decompress()

                // The full screenshot byte array is width * height
                assertThat(decompressedBytes.size).isEqualTo(
                    BITMAP_HEADER_SIZE + floatingDialog.width * floatingDialog.height)
                // Check the bitmap header
                assertThat(decompressedBytes.take(BITMAP_HEADER_SIZE)).isEqualTo(
                    ((floatingDialog.width).toBytes() + (floatingDialog.height).toBytes() +
                            BitmapType.ABGR_8888.byteVal).asList()
                )
                // Check the first few bytes to make sure they match our header,
                // that's enough to know that all the data went through correctly.
                assertThat(
                    decompressedBytes
                        .drop(BITMAP_HEADER_SIZE)
                        .take(floatingFakeBitmapHeader.size)
                ).isEqualTo(
                    floatingFakeBitmapHeader.asList()
                )
            }
        }
    }

    @Test
    fun canFetchPropertiesForView() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val root = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            addView(View(context))
            addView(TextView(context, "Placeholder Text"))
            addView(AppCompatButton(context, "Button", 0xFF0000, 0x00FF88))
        }

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        run { // Search for properties for View
            val viewChild = root.getChildAt(0)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = viewChild.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    assertThat(propertyGroup.viewId).isEqualTo(viewChild.uniqueDrawingId)
                    assertThat(propertyGroup.propertyList.map { strings[it.name] }).containsExactly(
                        "visibility", "layout_width", "layout_height"
                    )
                }
            }
        }

        run { // Search for properties for TextView
            val textChild = root.getChildAt(1)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = textChild.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    assertThat(propertyGroup.viewId).isEqualTo(textChild.uniqueDrawingId)
                    assertThat(propertyGroup.propertyList.map { strings[it.name] }).containsExactly(
                        "text", "visibility", "layout_width", "layout_height"
                    )
                }
            }
        }

        run { // Search for properties for AppCompatButton
            val button = root.getChildAt(2)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = button.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    val props = propertyGroup.propertyList.map {
                        "${strings[it.namespace]}.${strings[it.name]}"
                    }
                    assertThat(propertyGroup.viewId).isEqualTo(button.uniqueDrawingId)
                    assertThat(props).containsExactly(
                        "android.text",
                        "android.visibility",
                        "android.layout_width",
                        "android.layout_height",
                        "android.backgroundTint",
                        "$packageName.backgroundTint"
                    )
                }
            }
        }
    }

    // WebView trampolines onto a different thread when reading properties, so just make sure things
    // continue to work in that case.
    @Test
    fun canFetchPropertiesForWebView() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val root = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            addView(View(context))
            addView(WebView(context))
            addView(View(context))
        }

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        run { // Search for properties for WebView
            val viewChild = root.getChildAt(1)

            val getPropertiesCommand = Command.newBuilder().apply {
                getPropertiesCommandBuilder.apply {
                    rootViewId = root.uniqueDrawingId
                    viewId = viewChild.uniqueDrawingId
                }
            }.build()
            viewInspector.onReceiveCommand(
                getPropertiesCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.GET_PROPERTIES_RESPONSE)
                response.getPropertiesResponse.let { propertiesResponse ->
                    val strings = StringTable.fromStringEntries(propertiesResponse.stringsList)
                    val propertyGroup = propertiesResponse.propertyGroup
                    assertThat(propertyGroup.viewId).isEqualTo(viewChild.uniqueDrawingId)
                    assertThat(propertyGroup.propertyList.map { strings[it.name] }).containsExactly(
                        "visibility", "layout_width", "layout_height"
                    )
                }
            }
        }
    }


    @Test
    fun correctScreenshotTypeForConnectType() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(5)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val fakeBitmapHeader = byteArrayOf(1, 2, 3) // trailed by 0s
        val fakePicture1 = Picture(byteArrayOf(2, 1)) // Will be ignored because of BITMAP mode

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val root = ViewGroup(context).apply {
            width = 100
            height = 200
            setAttachInfo(View.AttachInfo())
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))
        run {
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
                assertThat(response.startFetchResponse.error).isEmpty()
            }
            ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

            // We connected in live mode, so the screenshot type should be bitmap
            root.viewRootImpl = ViewRootImpl()
            root.viewRootImpl.mSurface = Surface()
            root.viewRootImpl.mSurface.bitmapBytes = fakeBitmapHeader
            root.forcePictureCapture(fakePicture1)
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            }
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
                assertThat(event.layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
            }
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

        run {
            // reconnect in non-live mode
            val startFetchCommand = Command.newBuilder().apply {
                startFetchCommandBuilder.apply {
                    continuous = false
                }
            }.build()
            viewInspector.onReceiveCommand(
                startFetchCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
                assertThat(response.startFetchResponse.error).isEmpty()
            }
            ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

            // Now the screenshot should be an skp
            root.forcePictureCapture(fakePicture1)
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            }
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
                assertThat(event.layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
            }
        }
    }

    @Test
    fun settingScreenshotTypeAffectsCaptureOutput() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(5)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val fakeBitmapHeader = byteArrayOf(1, 123, 121) // trailed by 0s
        val fakePicture1 = Picture(byteArrayOf(2, 1)) // Will be ignored because of BITMAP mode
        val fakePicture2 = Picture(byteArrayOf(2, 2))

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val scale = 0.5
        val scale2 = 0.2
        val root = ViewGroup(context).apply {
            width = 100
            height = 200
            setAttachInfo(View.AttachInfo())
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

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
            assertThat(response.startFetchResponse.error).isEmpty()
        }
        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        run { // Start first by setting type to BITMAP
            val updateScreenshotTypeCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.BITMAP
                    this.scale = scale.toFloat()
                }
            }.build()
            viewInspector.onReceiveCommand(
                updateScreenshotTypeCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
            }
            ThreadUtils.runOnMainThread { }.get() // Wait for notifications to be reset
            root.viewRootImpl = ViewRootImpl()
            root.viewRootImpl.mSurface = Surface()
            root.viewRootImpl.mSurface.bitmapBytes = fakeBitmapHeader
            root.forcePictureCapture(fakePicture1)
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            }
            val check = { event: Event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)

                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
                    val decompressedBytes = screenshot.bytes.toByteArray().decompress()

                    // The full screenshot byte array is width * height
                    assertThat(decompressedBytes.size).isEqualTo(
                        (BITMAP_HEADER_SIZE + root.width * scale * root.height * scale).toInt()
                    )
                    // Check the bitmap header
                    assertThat(decompressedBytes.take(BITMAP_HEADER_SIZE)).isEqualTo(
                        ((root.width * scale).toInt().toBytes() +
                                (root.height * scale).toInt().toBytes() +
                                BitmapType.RGB_565.byteVal).asList()
                    )
                    // Check the first few bytes to make sure they match our header,
                    // that's enough to know that all the data went through correctly.
                    assertThat(
                        decompressedBytes
                            .drop(BITMAP_HEADER_SIZE)
                            .take(fakeBitmapHeader.size)
                    ).isEqualTo(
                        fakeBitmapHeader.asList()
                    )
                }
            }
            checkNonProgressEvent(eventQueue, check)
            // There will be a second one to capture the end of any animation.
            checkNonProgressEvent(eventQueue, check)

            // Send another event without the screenshot type specified and verify it isn't changed
            val dontUpdateScreenshotCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    this.scale = scale2.toFloat()
                }
            }.build()
            viewInspector.onReceiveCommand(
                dontUpdateScreenshotCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            ThreadUtils.runOnMainThread { }.get() // Wait for notifications to be reset

            responseQueue.take()
            root.forcePictureCapture(fakePicture1)
            val check2 = { event:Event ->
                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
                    val decompressedBytes = screenshot.bytes.toByteArray().decompress()
                    // verify the newly scaled size
                    assertThat(decompressedBytes.size).isEqualTo(
                        (BITMAP_HEADER_SIZE + root.width * scale2 * root.height * scale2).toInt())
                }
            }
            checkNonProgressEvent(eventQueue, check2)
            // Again there will be a second event
            checkNonProgressEvent(eventQueue, check2)
            // Send another event without the scale specified and verify it isn't changed
            val dontUpdateScaleCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.SKP
                }
            }.build()
            viewInspector.onReceiveCommand(
                dontUpdateScaleCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take()
            ThreadUtils.runOnMainThread { }.get() // Wait for notifications to be reset

            root.forcePictureCapture(fakePicture1)
            checkNonProgressEvent(eventQueue) { event ->
                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.SKP)
                    // We don't have a good way to test that the scale is the same in the SKP case
                }
            }
        }

        run { // Now set type back to SKP
            val updateScreenshotTypeCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.SKP
                }
            }.build()
            viewInspector.onReceiveCommand(
                updateScreenshotTypeCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            responseQueue.take().let { bytes ->
                val response = Response.parseFrom(bytes)
                assertThat(response.specializedCase).isEqualTo(
                    Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
            }

            root.forcePictureCapture(fakePicture2)
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)

                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.SKP)
                    assertThat(screenshot.bytes.toByteArray()).isEqualTo(fakePicture2.bytes)
                }
            }
            // Send another event without the screenshot type specified and verify it isn't changed
            val dontUpdateScreenshotCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    this.scale = scale2.toFloat()
                }
            }.build()
            viewInspector.onReceiveCommand(
                dontUpdateScreenshotCommand.toByteArray(),
                inspectorRule.commandCallback
            )

            responseQueue.take()
            root.forcePictureCapture(fakePicture2)
            checkNonProgressEvent(eventQueue) { event ->
                assertThat(event.layoutEvent.screenshot.type).isEqualTo(Screenshot.Type.SKP)
            }

            // Send another event without the scale specified and verify it isn't changed
            val dontUpdateScaleCommand = Command.newBuilder().apply {
                updateScreenshotTypeCommandBuilder.apply {
                    type = Screenshot.Type.BITMAP
                }
            }.build()
            viewInspector.onReceiveCommand(
                dontUpdateScaleCommand.toByteArray(),
                inspectorRule.commandCallback
            )
            ThreadUtils.runOnMainThread { }.get() // Wait for notifications to be reset

            responseQueue.take()
            root.forcePictureCapture(fakePicture2)
            checkNonProgressEvent(eventQueue) { event ->
                event.layoutEvent.screenshot.let { screenshot ->
                    assertThat(screenshot.type).isEqualTo(Screenshot.Type.BITMAP)
                    val decompressedBytes = screenshot.bytes.toByteArray().decompress()
                    // verify the scaled size
                    assertThat(decompressedBytes.size).isEqualTo(
                        (BITMAP_HEADER_SIZE + root.width * scale2 * root.height * scale2).toInt())
                    // Check the bitmap header
                    assertThat(decompressedBytes.take(BITMAP_HEADER_SIZE)).isEqualTo(
                        ((root.width * scale2).toInt().toBytes() +
                                (root.height * scale2).toInt().toBytes() +
                                BitmapType.RGB_565.byteVal).asList()
                    )
                }
            }
        }
    }

    @Test
    fun settingScreenshotTypeToExistingDoesntTriggerInvalidate() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(2)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val scale = 0.5
        val root = ViewGroup(context).apply {
            width = 100
            height = 200
            setAttachInfo(View.AttachInfo())
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = false
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
            assertThat(response.startFetchResponse.error).isEmpty()
        }
        // Wait for startCommand to finish initializing
        ThreadUtils.runOnMainThread { }.get(10, TimeUnit.SECONDS) ?: throw TimeoutException()

        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.BITMAP
                this.scale = scale.toFloat()
            }
        }.build()
        val initialInvalidateCount = ThreadUtils.runOnMainThread { root.invalidateCount }.get()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.poll(10, TimeUnit.SECONDS) ?: throw TimeoutException()

        // Validate that setting the screenshot type and scale caused an invalidation
        ThreadUtils.runOnMainThread {
            // Access root on the main thread. This also ensures this logic runs after the
            // invalidation call that occurs in ViewLayoutInspector#handleUpdateScreenshotType
            assertThat(root.invalidateCount).isEqualTo(initialInvalidateCount + 1)
        }.get(10, TimeUnit.SECONDS) ?: throw TimeoutException()

        // Send the same values again and verify that no invalidation happened
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.poll(10, TimeUnit.SECONDS) ?: throw TimeoutException()

        ThreadUtils.runOnMainThread {
            assertThat(root.invalidateCount).isEqualTo(initialInvalidateCount + 1)
        }.get(10, TimeUnit.SECONDS) ?: throw TimeoutException()

        // Make another change and verify that in invalidation did happen
        val updateScreenshotTypeCommand2 = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand2.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.poll(10, TimeUnit.SECONDS) ?: throw TimeoutException()

        ThreadUtils.runOnMainThread {
            assertThat(root.invalidateCount).isEqualTo(initialInvalidateCount + 2)
        }.get(10, TimeUnit.SECONDS) ?: throw TimeoutException()
    }

    @Test
    fun noHardwareAccelerationDuringStartReturnsError() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }
        val context = Context("view.inspector.test", Resources(mutableMapOf()))
        val root = View(context).apply { setAttachInfo(View.AttachInfo())}
        // FLAG_HARDWARE_ACCELERATED will be false
        root.layoutParams = WindowManager.LayoutParams()
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

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
            assertThat(response.specializedCase)
                .isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
            assertThat(response.startFetchResponse.error)
                .isEqualTo("Activity must be hardware accelerated for live inspection")
            assertThat(response.startFetchResponse.code)
                .isEqualTo(ErrorCode.NO_HARDWARE_ACCELERATION)
        }
    }

    @Test
    fun noRootViewsReturnError() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
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
            assertThat(response.specializedCase)
                .isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
            assertThat(response.startFetchResponse.error)
                .isEqualTo("Unable to find any root Views")
            assertThat(response.startFetchResponse.code)
                .isEqualTo(ErrorCode.NO_ROOT_VIEWS_FOUND)
        }
    }

    @Test
    fun progressEventsSent() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val root = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val fakePicture = Picture(byteArrayOf(1, 2, 3))
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
        }

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                // Set continuous to false since we don't need it to be true to clear the root IDs
                // as a side effect
                continuous = false
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
            assertThat(response.startFetchResponse.error).isEmpty()
        }

        root.forcePictureCapture(fakePicture)
        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        for ((case, checkpoint) in listOf(
            Event.SpecializedCase.PROGRESS_EVENT to ProgressCheckpoint.START_RECEIVED,
            Event.SpecializedCase.PROGRESS_EVENT to ProgressCheckpoint.STARTED,
            Event.SpecializedCase.PROGRESS_EVENT to ProgressCheckpoint.VIEW_INVALIDATION_CALLBACK,
            Event.SpecializedCase.ROOTS_EVENT to null,
            Event.SpecializedCase.PROGRESS_EVENT to ProgressCheckpoint.SCREENSHOT_CAPTURED,
            Event.SpecializedCase.PROGRESS_EVENT to ProgressCheckpoint.VIEW_HIERARCHY_CAPTURED,
            Event.SpecializedCase.PROGRESS_EVENT to ProgressCheckpoint.RESPONSE_SENT,
            Event.SpecializedCase.LAYOUT_EVENT to null,
            Event.SpecializedCase.PROPERTIES_EVENT to null
        )) {
            eventQueue.take().let { bytes ->
                val event = Event.parseFrom(bytes)
                assertThat(event.specializedCase).isEqualTo(case)
                if (case == Event.SpecializedCase.PROGRESS_EVENT) {
                    assertThat(event.progressEvent.checkpoint).isEqualTo(checkpoint)
                }
            }
        }
    }

    @Test
    fun treeWithEmptyRoots() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(5)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val mainScreen = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 400
            height = 800
        }

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(mainScreen))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        // Remove the root views simulating the app going into the background
        WindowManagerGlobal.getInstance().rootViews.clear()

        mainScreen.viewRootImpl = ViewRootImpl()
        mainScreen.viewRootImpl.mSurface = Surface()
        mainScreen.viewRootImpl.mSurface.bitmapBytes = byteArrayOf(1, 2, 3)
        mainScreen.forcePictureCapture(Picture(byteArrayOf(1)))

        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }
    }

    // This test starts two captures at the same time, after stopping capturing.
    // The first capture have isLastCapture = true and will return a LAYOUT_EVENT. The second
    // capture should return without an exception.
    @Test
    fun cancelWhileCapturing() = createViewInspector { viewInspector ->
        val eventQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }
        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val mainScreen = ViewGroup(context).apply {
            setAttachInfo(View.AttachInfo())
            width = 400
            height = 800
        }

        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(mainScreen))

        // Set up the two capture threads.
        var exception: Exception? = null
        val t1 = thread(start = false) {
            try {
                mainScreen.forcePictureCapture(Picture(byteArrayOf(1)))
            }
            catch (e: Exception) {
                exception = e
            }
        }
        val t2 = thread(start = false) {
            try {
                mainScreen.forcePictureCapture(Picture(byteArrayOf(1)))
            }
            catch (e: Exception) {
                exception = e
            }
        }

        // Set up the synchronization between the threads, so both requests start before the
        // executors are shut down and neither thinks it's obsolete and exits right away.
        // t1 will run and send events, and t2 will exit because the inner executor has already been
        // removed from the context.
        val t2StartedExecution = CountDownLatch(1)
        val receivedLayoutResponse = CountDownLatch(1)
        viewInspector.basicExecutorFactory = { body: (Runnable) -> Unit ->
            Executor {
                if (Thread.currentThread() == t1) {
                    t2StartedExecution.await()
                }
                if (Thread.currentThread() == t2) {
                    t2StartedExecution.countDown()
                    receivedLayoutResponse.await()
                }
                body(it)
            }
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
        responseQueue.remove()
        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.remove()

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

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing
        mainScreen.viewRootImpl = ViewRootImpl()
        mainScreen.viewRootImpl.mSurface = Surface()
        mainScreen.viewRootImpl.mSurface.bitmapBytes = byteArrayOf(1, 2, 3)

        t1.start()
        t2.start()
        checkNonProgressEvent(eventQueue) { event ->
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
        }
        checkNonProgressEvent(eventQueue) { event ->
            receivedLayoutResponse.countDown()
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
        }

        t1.join()
        t2.join()
        assertThat(exception).isNull()
    }

    // Check that taking a snapshot doesn't result a missing capturing callback: b/234407838
    // TODO: Add check of properties.
    @Test
    fun takeSnapshot() = createViewInspector { viewInspector ->
        val responseQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.add(bytes)
        }

        val eventQueue = ArrayBlockingQueue<ByteArray>(10)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.add(bytes)
        }

        val packageName = "view.inspector.test"
        val resources = createResources(packageName)
        val context = Context(packageName, resources)
        val root = ViewGroup(context).apply {
            width = 100
            height = 200
            setAttachInfo(View.AttachInfo())
            pictureCapture = Picture(byteArrayOf(1, 2, 3))
            viewRootImpl = ViewRootImpl().apply {
                mSurface = Surface().apply {
                    bitmapBytes = byteArrayOf(1, 2, 3) // trailed by 0s
                }
            }
        }
        WindowManagerGlobal.getInstance().rootViews.addAll(listOf(root))

        val updateScreenshotTypeCommand = Command.newBuilder().apply {
            updateScreenshotTypeCommandBuilder.apply {
                type = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            updateScreenshotTypeCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.UPDATE_SCREENSHOT_TYPE_RESPONSE)
        }

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                // Set continuous to false since we don't need it to be true to clear the root IDs
                // as a side effect
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
            assertThat(response.startFetchResponse.error).isEmpty()
        }

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        checkNonProgressEvents(eventQueue,
            Event.SpecializedCase.ROOTS_EVENT,
            Event.SpecializedCase.LAYOUT_EVENT,
        )

        val captureSnapshotCommand = Command.newBuilder().apply {
            captureSnapshotCommandBuilder.apply {
                screenshotType = Screenshot.Type.SKP
            }
        }.build()
        viewInspector.onReceiveCommand(
            captureSnapshotCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        ThreadUtils.runOnMainThread { }.get() // Wait for captureCommand to finish initializing

        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.CAPTURE_SNAPSHOT_RESPONSE)
            assertThat(response.captureSnapshotResponse.windowRoots.idsList).containsExactly(root.uniqueDrawingId)
        }

        // Make sure the live mode is still active:
        root.invalidate()
        ThreadUtils.runOnMainThread { }.get() // Wait for the capture to happen

        checkNonProgressEvents(eventQueue,
            Event.SpecializedCase.LAYOUT_EVENT,
        )
    }

    private fun checkNonProgressEvent(
        eventQueue: BlockingQueue<ByteArray>, block: (Event) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        var found = false
        while (startTime + TimeUnit.SECONDS.toMillis(10) > System.currentTimeMillis()) {
            val bytes = eventQueue.poll(10, TimeUnit.SECONDS) ?: throw TimeoutException()
            val event = Event.parseFrom(bytes)
            if (event.specializedCase == Event.SpecializedCase.PROGRESS_EVENT) {
                // skip progress events for this test
                continue
            }
            block(event)
            found = true
            break
        }
        assertThat(found).isTrue()
    }

    private fun checkNonProgressEvents(
        eventQueue: BlockingQueue<ByteArray>,
        vararg expectedEventTypes: Event.SpecializedCase
    ) {
        for (type in expectedEventTypes) {
            checkNonProgressEvent(eventQueue) { assertThat(it.specializedCase).isEqualTo(type) }
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

        try {
            block(viewInspector)
        }
        finally {
            viewInspector.onDispose()
            Looper.getLoopers().keys
                .filter { !initialThreads.contains(it) }
                .forEach { thread -> thread.join(TimeUnit.SECONDS.toMillis(1)) }
        }
    }

    @Suppress("SameParameterValue")
    private fun createResources(packageName: String): Resources {
        val resourceNames = mutableMapOf<Int, String>()
        ViewInspectionCompanion.addResourceNames(resourceNames)
        ViewGroupLayoutParamsInspectionCompanion.addResourceNames(resourceNames)
        TextViewInspectionCompanion.addResourceNames(resourceNames)
        ButtonInspectionCompanion.addResourceNames(resourceNames)
        AppCompatButtonInspectionCompanion.addResourceNames(packageName, resourceNames)
        return Resources(resourceNames)
    }
}
