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
import com.android.tools.agent.appinspection.proto.createConfiguration
import com.android.tools.agent.appinspection.proto.createGetPropertiesResponse
import com.android.tools.agent.appinspection.proto.createPropertyGroup
import com.android.tools.agent.appinspection.proto.toNode
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.agent.appinspection.util.compress
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.layoutinspector.BitmapType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Configuration
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Point
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeCommand
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeResponse
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    private data class ScreenshotSettings(
        val type: Screenshot.Type,
        val scale: Float = 1.0f
    )

    /**
     * A class which provides [checkRoots] for checking to see if the roots view IDs have changed
     * since a previous check. Any time roots or added or removed, this class will generate and
     * send a [WindowRootsEvent].
     *
     * While the framework informs us about most screen changes, sometimes a dialog can close
     * without us being informed, so [start] is provided to spin up a background thread that checks
     * occasionally. So even though [checkRoots] can be called directly, we still have a backup to
     * handle updates that the system doesn't tell us about.
     */
    private inner class RootsDetector {
        private var quit = AtomicBoolean(false)
        private var checkRootsThread: Thread? = null

        /**
         * A snapshot of the current list of view root IDs.
         *
         * We'll occasionally check against these against the current list of roots, generating an
         * event if they've ever changed.
         */
        var lastRootIds = emptySet<Long>()

        /**
         * Clear the state of this detector.
         *
         * After calling this function, it should be equivalent to this class after it was first
         * instantiated.
         */
        fun reset() {
            stop()
            lastRootIds = emptySet()
        }

        /**
         * Start running a thread which will periodically call [checkRoots].
         *
         * If the thread is still running from a previous call to [start], it will be stopped,
         * state cleared, and restarted. There is no need to call [reset] yourself if calling this
         * method, in other words.
         */
        fun start() {
            reset()

            checkRootsThread = ThreadUtils.newThread {
                while (!quit.get()) {
                    checkRoots()
                    Thread.sleep(200)
                }
            }.also {
                it.start()
            }
        }

        /**
         * Stop the thread if started by [start]. This method blocks until the thread has finished.
         */
        fun stop() {
            checkRootsThread?.let { thread ->
                quit.set(true)
                thread.join()
                quit.set(false)
                checkRootsThread = null
            }
        }

        /**
         * This method checks to see if any views were added or removed since the last time it was
         * called.
         *
         * If anything did change, this method will sends a [WindowRootsEvent] to the host to inform
         * them. It will also stop any stale roots from capturing and, depending on
         * [InspectorState.fetchContinuously], may start capturing new roots.
         */
        @Synchronized
        fun checkRoots() {
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
                    if (state.fetchContinuously) {
                        if (added.isNotEmpty()) {
                            // The first time we call this method, `lastRootIds` gets initialized
                            // with views already being captured, so we don't need to start
                            // capturing them again.
                            val actuallyAdded = added.toMutableList().apply {
                                removeAll { id -> state.contextMap.containsKey(id) }
                            }
                            if (actuallyAdded.isNotEmpty()) {
                                ThreadUtils.runOnMainThread {
                                    for (toAdd in added) {
                                        startCapturing(currRoots.getValue(toAdd))
                                    }
                                }
                            }
                        }
                        else if (removed.isNotEmpty()) {
                            ThreadUtils.runOnMainThread {
                                // When a window goes away, we expect remaining views to send a
                                // signal causing the client to refresh, but this doesn't always
                                // happen, so to be safe, we force it ourselves.
                                currRoots.values.forEach { view -> view.invalidate() }
                            }
                        }
                    }
                }
            }
        }
    }

    private class SnapshotRequest {
        enum class State { NEW, PROCESSING }
        val result = CompletableDeferred<LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot>()
        val state = AtomicReference(State.NEW)
    }

    private class InspectorState {
        /**
         * A mapping of root view IDs to data that should be accessed across multiple threads.
         */
        val contextMap = mutableMapOf<Long, CaptureContext>()

        /**
         * When true, the inspector will keep generating layout events as the screen changes.
         * Otherwise, it will only return a single layout snapshot before going back to waiting.
         */
        var fetchContinuously: Boolean = false

        /**
         * When true, future layout events should exclude System views, only returning trees of
         * views created by the user's app.
         */
        var skipSystemViews: Boolean = false

        /**
         * Settings that determine the format of screenshots taken when doing a layout capture.
         */
        var screenshotSettings = ScreenshotSettings(Screenshot.Type.BITMAP)

        /**
         * When a snapshot is requested an entry will be added to this map for each window. Then
         * when content for that window is processed it will be set into the Deferred rather than
         * sent back as a normal Event.
         */
        var snapshotRequests: MutableMap<Long, SnapshotRequest> = ConcurrentHashMap()
    }

    private val stateLock = Any()
    @GuardedBy("stateLock")
    private val state = InspectorState()

    private val rootsDetector = RootsDetector()

    private var previousConfig = Configuration.getDefaultInstance()

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
            Command.SpecializedCase.CAPTURE_SNAPSHOT_COMMAND -> handleCaptureSnapshotCommand(
                command.captureSnapshotCommand,
                callback
            )
            else -> error("Unexpected view inspector command case: ${command.specializedCase}")
        }
    }

    override fun onDispose() {
        forceStopAllCaptures()
        SynchronousPixelCopy.stopHandler()
    }

    private fun forceStopAllCaptures() {
        rootsDetector.stop()
        synchronized(stateLock) {
            for (context in state.contextMap.values) {
                context.handle.close()
            }
            state.contextMap.clear()
        }
    }

    private fun startCapturing(root: View) {
        // Starting rendering captures must be called on the View thread or else it throws
        ThreadUtils.assertOnMainThread()

        val os = ByteArrayOutputStream()
        // We might get multiple callbacks for the same view while still processing an earlier
        // one. Let's avoid processing these in parallel to avoid confusion.
        val sequentialExecutor = Executors.newSingleThreadExecutor { r -> ThreadUtils.newThread(r) }

        val captureExecutor = Executor { command ->
            sequentialExecutor.execute {
                var snapshotRequest: SnapshotRequest?
                var context: CaptureContext
                var screenshotSettings: ScreenshotSettings
                var skipSystemViews: Boolean
                synchronized(stateLock) {
                    snapshotRequest = state.snapshotRequests[root.uniqueDrawingId]
                    if (snapshotRequest?.state?.compareAndSet(
                            SnapshotRequest.State.NEW, SnapshotRequest.State.PROCESSING) != true) {
                        snapshotRequest = null
                    }
                    if (snapshotRequest != null) {
                        skipSystemViews = false
                        screenshotSettings = ScreenshotSettings(Screenshot.Type.SKP)
                    }
                    else {
                        skipSystemViews = state.skipSystemViews
                        screenshotSettings = state.screenshotSettings
                    }
                    // We might get some lingering captures even though we already finished
                    // listening earlier (this would be indicated by no context). Just abort
                    // early in that case.
                    // Note: We copy the context instead of returning it directly, to avoid rare
                    // but potential threading issues as other threads can modify the context, e.g.
                    // handling the stop fetch command.
                    context = state.contextMap[root.uniqueDrawingId]?.copy() ?: return@execute
                    if (snapshotRequest == null && context.isLastCapture) {
                        state.contextMap.remove(root.uniqueDrawingId)
                    }
                }

                // Just in case, always check roots before sending a layout event, as this may send
                // out a roots event. We always want layout events to follow up-to-date root events.
                rootsDetector.checkRoots()

                val snapshotResponse = if (snapshotRequest != null) {
                    LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.newBuilder()
                }
                else null

                run { // Prepare and send LayoutEvent
                    // Triggers image fetch into `os`
                    // We always have to do this even if we don't use the bytes it gives us,
                    // because otherwise an internal queue backs up
                    command.run()

                    // If we have a snapshot request, we can remove it from the request map now that
                    // it's no longer needed to indicate that SKPs need to be collected.
                    if (snapshotRequest != null) {
                        state.snapshotRequests.remove(root.uniqueDrawingId)
                    }

                    val screenshot = when(screenshotSettings.type) {
                        Screenshot.Type.SKP -> ByteString.copyFrom(os.toByteArray())
                        Screenshot.Type.BITMAP -> {
                            // If this is the lowest z-index window (the normal case) we can be more
                            // efficient because we don't need alpha information.
                            val bitmapType =
                                if (root.uniqueDrawingId == rootsDetector.lastRootIds.first())
                                    BitmapType.RGB_565 else BitmapType.ABGR_8888
                            root.takeScreenshot(screenshotSettings.scale, bitmapType)
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
                    val configuration = root.createConfiguration(stringTable)

                    val (rootView, rootOffset) = ThreadUtils.runOnMainThread {
                        val rootView = root.toNode(stringTable, skipSystemViews)
                        val rootOffset = IntArray(2)
                        root.getLocationInSurface(rootOffset)

                        (rootView to rootOffset)
                    }.get()

                    val layout = LayoutEvent.newBuilder().apply {
                        addAllStrings(stringTable.toStringEntries())
                        this.appContext = appContext
                        if (configuration != previousConfig) {
                            previousConfig = configuration
                            this.configuration = configuration
                        }
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
                    if (snapshotResponse != null) {
                        snapshotResponse.layout = layout
                    }
                    else {
                        connection.sendEvent {
                            layoutEvent = layout
                        }
                    }
                }

                if (snapshotResponse != null || context.isLastCapture) { // Prepare and send PropertiesEvent
                    // We get here either if the client requested a one-time snapshot of the layout
                    // or if the client just stopped an in-progress fetch. Collect and send all
                    // properties, so that the user can continue to explore all values in the UI and
                    // they will match exactly the layout at this moment in time.

                    val allViews = ThreadUtils.runOnMainThread { root.flatten().toList() }.get()
                    val stringTable = StringTable()
                    val propertyGroups = allViews.map { it.createPropertyGroup(stringTable) }
                    val properties = PropertiesEvent.newBuilder().apply {
                        rootId = root.uniqueDrawingId
                        addAllPropertyGroups(propertyGroups)
                        addAllStrings(stringTable.toStringEntries())
                    }.build()
                    if (snapshotResponse != null) {
                        snapshotResponse.properties = properties
                    }
                    else {
                        connection.sendEvent {
                            propertiesEvent = properties
                        }
                    }
                }
                snapshotResponse?.let { snapshotRequest?.result?.complete(it.build()) }
            }
        }

        try {
            synchronized(stateLock) {
                val handle =
                    SkiaQWorkaround.startRenderingCommandsCapture(
                        root,
                        captureExecutor,
                        callback = { os },
                        shouldSerialize = { state.snapshotRequests.isNotEmpty() ||
                                state.screenshotSettings.type == Screenshot.Type.SKP })
                if (handle != null) {
                    state.contextMap[root.uniqueDrawingId] =
                        CaptureContext(handle, isLastCapture = (!state.fetchContinuously))
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
            state.fetchContinuously = startFetchCommand.continuous
            state.skipSystemViews = startFetchCommand.skipSystemViews
        }

        if (startFetchCommand.continuous) {
            rootsDetector.start()
        }
        else {
            // We may be getting here after a previous start / stop flow
            rootsDetector.reset()
        }
        ThreadUtils.runOnMainThread {
            for (root in getRootViews()) {
                startCapturing(root)
            }
        }
    }

    private fun handleUpdateScreenshotType(
        updateScreenshotTypeCommand: UpdateScreenshotTypeCommand,
        callback: CommandCallback
    ) {
        var changed: Boolean
        synchronized(stateLock) {
            val oldSettings = state.screenshotSettings
            val newSettings = updateScreenshotTypeCommand.let {
                ScreenshotSettings(
                    it.type.takeIf { type -> type != Screenshot.Type.UNKNOWN } ?: oldSettings.type,
                    it.scale.takeIf { scale -> scale > 0f } ?: oldSettings.scale)
            }
            changed = (oldSettings != newSettings)
            state.screenshotSettings = newSettings
        }
        callback.reply {
            updateScreenshotTypeResponse = UpdateScreenshotTypeResponse.getDefaultInstance()
        }

        if (changed) {
            ThreadUtils.runOnMainThread {
                for (rootView in getRootViews()) {
                    rootView.invalidate()
                }
            }
        }
    }

    private fun handleStopFetchCommand(callback: CommandCallback) {
        callback.reply {
            stopFetchResponse = StopFetchResponse.getDefaultInstance()
        }

        rootsDetector.stop()
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

    private fun handleCaptureSnapshotCommand(
        // TODO: support bitmap
        captureSnapshotCommand: LayoutInspectorViewProtocol.CaptureSnapshotCommand,
        callback: CommandCallback
    ) {
        rootsDetector.checkRoots()
        state.snapshotRequests.clear()

        CoroutineScope(environment.executors().primary().asCoroutineDispatcher()).launch {
            val roots = ThreadUtils.runOnMainThreadAsync { getRootViews() }.await()
            val windowSnapshots = roots.map { view ->
                SnapshotRequest().also {
                    state.snapshotRequests[view.uniqueDrawingId] = it
                }.result
            }
            ThreadUtils.runOnMainThread { roots.forEach { it.invalidate() } }

            val reply = LayoutInspectorViewProtocol.CaptureSnapshotResponse.newBuilder().apply {
                windowRoots = WindowRootsEvent.newBuilder().apply {
                    addAllIds(roots.map { it.uniqueDrawingId })
                }.build()
                addAllWindowSnapshots(windowSnapshots.awaitAll())
            }.build()
            callback.reply {
                captureSnapshotResponse = reply
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
