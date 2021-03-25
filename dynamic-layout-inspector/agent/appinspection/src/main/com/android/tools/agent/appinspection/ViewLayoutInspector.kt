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

package com.android.tools.agent.appinspection

import android.view.View
import android.view.inspector.WindowInspector
import androidx.annotation.GuardedBy
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorFactory
import com.android.tools.agent.appinspection.framework.SkiaQWorkaround
import com.android.tools.agent.appinspection.framework.SynchronousPixelCopy
import com.android.tools.agent.appinspection.framework.flatten
import com.android.tools.agent.appinspection.framework.takeScreenshot
import com.android.tools.agent.appinspection.framework.toByteArray
import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.proto.createAppContext
import com.android.tools.agent.appinspection.proto.createGetPropertiesResponse
import com.android.tools.agent.appinspection.proto.createPropertyGroup
import com.android.tools.agent.appinspection.proto.toNode
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.agent.appinspection.util.compress
import com.android.tools.idea.protobuf.ByteString
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val LAYOUT_INSPECTION_ID = "layoutinspector.view.inspection"

// created by java.util.ServiceLoader
class ViewLayoutInspectorFactory : InspectorFactory<ViewLayoutInspector>(LAYOUT_INSPECTION_ID) {
    override fun createInspector(
        connection: Connection,
        environment: InspectorEnvironment
    ) = ViewLayoutInspector(connection, environment)
}

class ViewLayoutInspector(connection: Connection, private val environment: InspectorEnvironment) :
    Inspector(connection) {

    /**
     * Context data associated with a capture of a single layout tree.
     */
    private data class CaptureContext(
        /**
         * A handle returned by a system that does continuous capturing, which, when closed, tells
         * the system to stop as soon as possible.
         */
        val handle: AutoCloseable,
        /**
         * When true, indicates we should stop capturing after the next one
         */
        var isLastCapture: Boolean = false
    )

    private class ScreenshotSettings(
        val type: Screenshot.Type,
        val scale: Float = 1.0f
    )

    private class InspectorState {
        /**
         * A mapping of root view IDs to data that should be accessed across multiple threads.
         */
        val contextMap = mutableMapOf<Long, CaptureContext>()

        /**
         * When true, future layout events should exclude System views, only returning trees of
         * views created by the user's app.
         */
        var skipSystemViews: Boolean = false

        /**
         * Settings that determine the format of screenshots taken when doing a layout capture.
         */
        var screenshotSettings = ScreenshotSettings(Screenshot.Type.BITMAP)
    }

    private val stateLock = Any()
    @GuardedBy("stateLock")
    private val state = InspectorState()

    /**
     * A snapshot of the current list of view root IDs.
     *
     * We'll occasionally check against these against the current list of roots, generating an
     * event if they've ever changed.
     */
    private var lastRootIds = emptySet<Long>()

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = Command.parseFrom(data)
        when (command.specializedCase) {
            Command.SpecializedCase.START_FETCH_COMMAND -> handleStartFetchCommand(
                command.startFetchCommand,
                callback
            )
            Command.SpecializedCase.STOP_FETCH_COMMAND -> handleStopFetchCommand(callback)
            Command.SpecializedCase.GET_PROPERTIES_COMMAND -> handleGetProperties(
                command.getPropertiesCommand,
                callback
            )
            Command.SpecializedCase.UPDATE_SCREENSHOT_TYPE_COMMAND -> handleUpdateScreenshotType(
                command.updateScreenshotTypeCommand,
                callback
            )
            else -> error("Unexpected view inspector command case: ${command.specializedCase}")
        }
    }

    override fun onDispose() {
        forceStopAllCaptures()
        SynchronousPixelCopy.stopHandler()
    }

    /**
     * This method checks to see if any views were added or removed since the last time it was
     * called, returning true if so.
     *
     * As side effects, if anything did change, this method will sends a [WindowRootsEvent] to the
     * host to inform them. It will also stop any stale roots from capturing.
     *
     * @param captureNewRoots If this inspector is in continuous capture mode, this should be set
     *     to true, so that we automatically start capturing newly discovered views as well.
     */
    private fun checkRoots(captureNewRoots: Boolean): Boolean {
        val currRoots =
            ThreadUtils.runOnMainThread {
                getRootViews().associateBy { it.uniqueDrawingId }
            }.get()

        val currRootIds = currRoots.keys
        if (lastRootIds.size != currRootIds.size || !lastRootIds.containsAll(currRootIds)) {
            val removed = lastRootIds.filter { !currRootIds.contains(it) }
            val added = currRootIds.filter { !lastRootIds.contains(it) }
            lastRootIds = currRootIds
            connection.sendEvent {
                rootsEvent = WindowRootsEvent.newBuilder().apply {
                    addAllIds(currRootIds)
                }.build()
            }

            synchronized(stateLock) {
                for (toRemove in removed) {
                    state.contextMap.remove(toRemove)?.handle?.close()
                }
                if (captureNewRoots && added.isNotEmpty()) {
                    // The first time we call this method, `lastRootIds` gets initialized with views
                    // already being captured, so we don't need to start capturing them again.
                    val actuallyAdded = added.toMutableList().apply {
                        removeAll { id -> state.contextMap.containsKey(id) }
                    }
                    if (actuallyAdded.isNotEmpty()) {
                        ThreadUtils.runOnMainThread {
                            for (toAdd in added) {
                                startCapturing(currRoots.getValue(toAdd), continuous = true)
                            }
                        }
                    }
                }
            }
        }
        return lastRootIds === currRootIds
    }

    private fun forceStopAllCaptures() {
        synchronized(stateLock) {
            for (context in state.contextMap.values) {
                context.handle.close()
            }
            state.contextMap.clear()
        }
    }

    private fun startCapturing(root: View, continuous: Boolean) {
        // Starting rendering captures must be called on the View thread or else it throws
        ThreadUtils.assertOnMainThread()

        val os = ByteArrayOutputStream()
        // We might get multiple callbacks for the same view while still processing an earlier
        // one. Let's avoid processing these in parallel to avoid confusion.
        val sequentialExecutor = Executors.newSingleThreadExecutor { r -> ThreadUtils.newThread(r) }

        val captureExecutor = Executor { command ->
            sequentialExecutor.execute {
                var context: CaptureContext
                var screenshotSettings: ScreenshotSettings
                var skipSystemViews: Boolean
                synchronized(stateLock) {
                    skipSystemViews = state.skipSystemViews
                    screenshotSettings = state.screenshotSettings
                    // We might get some lingering captures even though we already finished
                    // listening earlier (this would be indicated by no context). Just abort
                    // early in that case.
                    // Note: We copy the context instead of returning it directly, to avoid rare
                    // but potential threading issues as other threads can modify the context, e.g.
                    // handling the stop fetch command.
                    context = state.contextMap[root.uniqueDrawingId]?.copy() ?: return@execute
                    if (context.isLastCapture) {
                        state.contextMap.remove(root.uniqueDrawingId)
                    }
                }

                // Check roots before sending a layout event, as this may send out a roots event.
                // We always want layout events to follow up-to-date root events.
                checkRoots(continuous)

                run { // Prepare and send LayoutEvent
                    // Triggers image fetch into `os`
                    // We always have to do this even if we don't use the bytes it gives us,
                    // because otherwise an internal queue backs up
                    command.run()
                    val screenshot = when(screenshotSettings.type) {
                        Screenshot.Type.SKP -> ByteString.copyFrom(os.toByteArray())
                        Screenshot.Type.BITMAP -> {
                            root.takeScreenshot(screenshotSettings.scale)
                                ?.toByteArray()
                                ?.compress()
                                ?.let { ByteString.copyFrom(it) }
                                ?: ByteString.EMPTY
                        }
                        else -> ByteString.EMPTY
                    }
                    os.reset() // Clear stream, ready for next frame

                    if (context.isLastCapture) {
                        context.handle.close()
                    }

                    val stringTable = StringTable()
                    val appContext = root.createAppContext(stringTable)

                    val (rootView, rootOffset) = ThreadUtils.runOnMainThread {
                        val rootView = root.toNode(stringTable, skipSystemViews)
                        val rootOffset = IntArray(2)
                        root.getLocationInSurface(rootOffset)

                        (rootView to rootOffset)
                    }.get()

                    connection.sendEvent {
                        layoutEvent = LayoutEvent.newBuilder().apply {
                            addAllStrings(stringTable.toStringEntries())
                            this.appContext = appContext
                            this.rootView = rootView
                            this.rootOffset = Point.newBuilder().apply {
                                x = rootOffset[0]
                                y = rootOffset[1]
                            }.build()
                            this.screenshot = Screenshot.newBuilder().apply {
                                type = screenshotSettings.type
                                bytes = screenshot
                            }.build()
                        }.build()
                    }
                }

                if (context.isLastCapture) { // Prepare and send PropertiesEvent
                    // We get here either if the client requested a one-time snapshot of the layout
                    // or if the client just stopped an in-progress fetch. Collect and send all
                    // properties, so that the user can continue to explore all values in the UI and
                    // they will match exactly the layout at this moment in time.

                    val allViews = ThreadUtils.runOnMainThread { root.flatten().toList() }.get()
                    val stringTable = StringTable()
                    val propertyGroups = allViews.map { it.createPropertyGroup(stringTable) }
                    connection.sendEvent {
                        propertiesEvent = PropertiesEvent.newBuilder().apply {
                            rootId = root.uniqueDrawingId
                            addAllPropertyGroups(propertyGroups)
                            addAllStrings(stringTable.toStringEntries())
                        }.build()
                    }
                }
            }
        }

        try {
            synchronized(stateLock) {
                val handle =
                    SkiaQWorkaround.startRenderingCommandsCapture(
                        root,
                        captureExecutor,
                        callback = { os },
                        shouldSerialize = { state.screenshotSettings.type == Screenshot.Type.SKP })
                if (handle != null) {
                    state.contextMap[root.uniqueDrawingId] =
                        CaptureContext(handle, isLastCapture = (!continuous))
                }
            }
            root.invalidate() // Force a re-render so we send the current screen
        }
        catch (t: Throwable) {
            connection.sendEvent {
                errorEvent = ErrorEvent.newBuilder().apply {
                    message = t.stackTraceToString()
                }.build()
            }
        }
    }

    private fun handleStartFetchCommand(
        startFetchCommand: StartFetchCommand,
        callback: CommandCallback
    ) {
        callback.reply {
            startFetchResponse = StartFetchResponse.getDefaultInstance()
        }

        forceStopAllCaptures()

        synchronized(stateLock) {
            state.skipSystemViews = startFetchCommand.skipSystemViews
        }

        ThreadUtils.runOnMainThread {
            for (root in getRootViews()) {
                startCapturing(root, startFetchCommand.continuous)
            }
        }
    }

    private fun handleUpdateScreenshotType(
        updateScreenshotTypeCommand: UpdateScreenshotTypeCommand,
        callback: CommandCallback
    ) {
        synchronized(stateLock) {
            state.screenshotSettings = ScreenshotSettings(
                updateScreenshotTypeCommand.type.let {
                    if (it == Screenshot.Type.UNKNOWN) state.screenshotSettings.type else it
                },
                updateScreenshotTypeCommand.scale.let {
                    if (it <= 0f) state.screenshotSettings.scale else it
                }
            )
        }
        callback.reply {
            updateScreenshotTypeResponse = UpdateScreenshotTypeResponse.getDefaultInstance()
        }

        ThreadUtils.runOnMainThread {
            for (rootView in getRootViews()) {
                rootView.invalidate()
            }
        }
    }


    private fun handleStopFetchCommand(callback: CommandCallback) {
        callback.reply {
            stopFetchResponse = StopFetchResponse.getDefaultInstance()
        }

        synchronized(stateLock) {
            val contextMap = state.contextMap
            for (context in contextMap.values) {
                context.isLastCapture = true
            }
            ThreadUtils.runOnMainThread {
                getRootViews()
                    .filter { view -> contextMap.containsKey(view.uniqueDrawingId) }
                    .forEach { view -> view.invalidate() }
            }
        }
    }

    private fun handleGetProperties(
        propertiesCommand: GetPropertiesCommand,
        callback: CommandCallback
    ) {

        ThreadUtils.runOnMainThread {
            val foundView = getRootViews()
                .asSequence()
                .filter { it.uniqueDrawingId == propertiesCommand.rootViewId }
                .flatMap { rootView -> rootView.flatten() }
                .filter { view -> view.uniqueDrawingId == propertiesCommand.viewId }
                .firstOrNull()

            environment.executors().primary().execute {
                val response =
                    foundView?.createGetPropertiesResponse()
                        ?: GetPropertiesResponse.getDefaultInstance()
                callback.reply { getPropertiesResponse = response }
            }
        }
    }
}

private fun getRootViews(): List<View> {
    ThreadUtils.assertOnMainThread()

    val views = WindowInspector.getGlobalWindowViews()
    return views
        .filter { view -> view.visibility == View.VISIBLE && view.isAttachedToWindow }
        .sortedBy { view -> view.z }
}

private fun Inspector.CommandCallback.reply(initResponse: Response.Builder.() -> Unit) {
    val response = Response.newBuilder()
    response.initResponse()
    reply(response.build().toByteArray())
}

private fun Connection.sendEvent(init: Event.Builder.() -> Unit) {
    sendEvent(
        Event.newBuilder()
            .apply { init() }
            .build()
            .toByteArray()
    )
}

private fun Throwable.stackTraceToString(): String {
    val error = ByteArrayOutputStream()
    printStackTrace(PrintStream(error))
    return error.toString()
}
