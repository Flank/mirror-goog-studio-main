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

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewDebug
import android.view.WindowManager
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
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
import com.android.tools.layoutinspector.errors.errorCode
import com.android.tools.layoutinspector.errors.noHardwareAcceleration
import com.android.tools.layoutinspector.errors.noRootViews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Configuration
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Point
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.timerTask

private const val LAYOUT_INSPECTION_ID = "layoutinspector.view.inspection"

// created by java.util.ServiceLoader
class ViewLayoutInspectorFactory : InspectorFactory<ViewLayoutInspector>(LAYOUT_INSPECTION_ID) {
    override fun createInspector(
        connection: Connection,
        environment: InspectorEnvironment
    ) = ViewLayoutInspector(connection, environment)
}

private const val MAX_START_FETCH_RETRIES = 10

class ViewLayoutInspector(connection: Connection, private val environment: InspectorEnvironment) :
    Inspector(connection) {

    @property:VisibleForTesting
    var basicExecutorFactory = { body: (Runnable) -> Unit -> Executor { body(it) } }

    private var checkpoint: ProgressCheckpoint = ProgressCheckpoint.NOT_STARTED
        set(value) {
            if (value <= field){
                return
            }
            field = value
            if (value != ProgressCheckpoint.NOT_STARTED) {
                connection.sendEvent {
                    progressEvent = ProgressEvent.newBuilder().apply {
                        checkpoint = field
                    }.build()
                }
            }
        }

    /**
     * Context data associated with a capture of a single layout tree.
     */
    private data class CaptureContext(
        /**
         * A handle returned by a system that does continuous capturing, which, when closed, tells
         * the system to stop as soon as possible.
         */
        val handle: AutoCloseable,
        val capturingType: Screenshot.Type,
        val root: View,
        val captureExecutor: Executor,
        val os: OutputStream,
        /**
         * Executor used during capture
         */
        val executorService: ExecutorService,
        /**
         * When true, indicates we should stop capturing after the next one
         */
        var isLastCapture: Boolean = false,
    ) {
        fun shutdown() {
            handle.close()
            executorService.shutdown()
        }
    }

    private data class ScreenshotSettings(
        val type: Screenshot.Type,
        val scale: Float = 1.0f
    )

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

    private val scope =
        CoroutineScope(SupervisorJob() + environment.executors().primary().asCoroutineDispatcher())

    private val stateLock = Any()
    @GuardedBy("stateLock")
    private val state = InspectorState()

    private val foldSupport = createFoldSupport(connection, { state.fetchContinuously })
        get() = foldSupportOverrideForTests ?: field

    @property:VisibleForTesting
    var foldSupportOverrideForTests: FoldSupport? = null

    private val rootsDetector =
        RootsDetector(connection, ::onRootsChanged) { checkpoint = it }

    private var previousConfig = Configuration.getDefaultInstance()
    private var previousContext = LayoutInspectorViewProtocol.AppContext.getDefaultInstance()

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
        foldSupport?.shutdown()
        scope.cancel("ViewLayoutInspector has been disposed")
        SynchronousPixelCopy.stopHandler()
    }

    /**
     * Stop any stale roots from capturing and, depending on [InspectorState.fetchContinuously],
     * may start capturing new roots.
     */
    private fun onRootsChanged(added: List<Long>, removed: List<Long>, roots: Map<Long, View>) {
        synchronized(stateLock) {
            for (toRemove in removed) {
                state.contextMap.remove(toRemove)?.shutdown()
            }
            added.mapNotNull { roots[it] }.forEach { foldSupport?.start(it) }
            removed.mapNotNull { roots[it] }.forEach { foldSupport?.stop(it) }

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
                                try {
                                    startCapturing(roots.getValue(toAdd))
                                } catch (t: Throwable) {
                                    Log.w("layinsp", t)
                                    connection.sendEvent {
                                        errorEvent =
                                            LayoutInspectorViewProtocol.ErrorEvent.newBuilder()
                                                .apply {
                                                    message = t.stackTraceToString()
                                                }.build()
                                    }
                                }
                            }
                        }
                    }
                }
                else if (removed.isNotEmpty()) {
                    ThreadUtils.runOnMainThread {
                        // When a window goes away, we expect remaining views to send a
                        // signal causing the client to refresh, but this doesn't always
                        // happen, so to be safe, we force it ourselves.
                        roots.values.forEach { view -> view.invalidate() }
                    }
                }
            }
        }
    }

    private fun forceStopAllCaptures() {
        rootsDetector.stop()
        synchronized(stateLock) {
            for (context in state.contextMap.values) {
                context.shutdown()
            }
            state.contextMap.clear()
        }
    }

    private fun startCapturing(root: View) {
        val params = root.layoutParams
        if (params is WindowManager.LayoutParams) {
            if (params.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED == 0) {
                rootsDetector.stop()
                throw noHardwareAcceleration()
            }
        }

        val os = ByteArrayOutputStream()
        val currentRequestId = AtomicLong()
        val captureExecutor = basicExecutorFactory { command ->
            val requestId = currentRequestId.incrementAndGet()
            checkpoint = ProgressCheckpoint.VIEW_INVALIDATION_CALLBACK
            val executor =
                state.contextMap[root.uniqueDrawingId]?.executorService
                    ?: return@basicExecutorFactory

            try {
                executor.execute {
                    if (requestId != currentRequestId.get()) {
                        // This request is obsolete, just return
                        command.run()
                        os.reset()
                        return@execute
                    }
                    executeCapture(command, os, root)
                }
            } catch (exception: RejectedExecutionException) {
                // this can happen if we stop capture and then start again immediately: "executor"
                // above can be shutdown in between when it's retrieved and when the execution
                // starts on the next line.
                connection.sendEvent {
                    errorEventBuilder.message =
                        "ViewLayoutInspector got RejectedExecutionException during capture: " +
                                exception.message
                }
            }
        }
        updateViewChangeNotification(root, captureExecutor, os)
        checkpoint = ProgressCheckpoint.STARTED
        root.invalidate() // Force a re-render so we send the current screen
    }

    private fun updateAllViewChangeNotifications() {
        for ((_, _, root, captureExecutor, os, _, _) in state.contextMap.values) {
            updateViewChangeNotification(root, captureExecutor, os)
        }
    }

    private fun updateViewChangeNotification(root: View, executor: Executor, os: OutputStream) {
        // Starting rendering captures must be called on the View thread or else it throws
        ThreadUtils.runOnMainThread {
            synchronized(stateLock) {
                var type =
                    if (state.snapshotRequests.isNotEmpty()) Screenshot.Type.SKP
                    else state.screenshotSettings.type
                if (type == state.contextMap[root.uniqueDrawingId]?.capturingType) {
                    // We already have this rendering type, just return
                    return@runOnMainThread
                }

                // The AutoClose implementation in ViewDebug will set the picture capture callback
                // to null in the renderer. Do not call this after creating a new callback.
                state.contextMap[root.uniqueDrawingId]?.handle?.close()

                var handle = if (type == Screenshot.Type.SKP) {
                    try {
                        // If we get null the view is gone. It will be removed by the roots detector
                        // later.
                        registerSkpCallback(root, executor, os) ?: return@runOnMainThread
                    }
                    catch (exception: Exception) {
                        connection.sendEvent {
                            errorEventBuilder.message =
                                "Unable to register listener for 3d mode images: ${exception.message}"
                        }
                        state.screenshotSettings =
                            ScreenshotSettings(Screenshot.Type.BITMAP, state.screenshotSettings.scale)
                        null
                    }
                } else null

                if (handle == null) {
                    handle = registerScreenshotCallback(root, executor, os)
                    type = Screenshot.Type.BITMAP
                }

                // We might get multiple callbacks for the same view while still processing an earlier
                // one. Let's avoid processing these in parallel to avoid confusion.
                val sequentialExecutor =
                    Executors.newSingleThreadExecutor { r -> ThreadUtils.newThread(r) }

                state.contextMap[root.uniqueDrawingId] =
                    CaptureContext(
                        handle,
                        type,
                        root,
                        executor,
                        os,
                        sequentialExecutor,
                        isLastCapture = (!state.fetchContinuously)
                    )
            }
        }
    }

    private fun registerSkpCallback(
        root: View,
        executor: Executor,
        os: OutputStream
    ) = if (Build.VERSION.SDK_INT > 32 ||
        (Build.VERSION.SDK_INT == 32  && Build.VERSION.PREVIEW_SDK_INT > 0)) {
        // This method is only accessible on T+ (or Q, but there it's broken).
        ViewDebug::class.java.getDeclaredMethod(
            "startRenderingCommandsCapture", View::class.java, Executor::class.java,
            Callable::class.java
        ).invoke(null, root, executor, Callable { os }) as AutoCloseable
    } else {
        SkiaQWorkaround.startRenderingCommandsCapture(root, executor) { os }
    }

    private fun registerScreenshotCallback(
        root: View,
        captureExecutor: Executor,
        os: OutputStream
    ): AutoCloseable {
        val timer = Timer("ViewLayoutInspectorTimer")
        var stop = false
        val doCapture = {
            if (!stop) {
                captureExecutor.execute {
                    // If this is the lowest z-index window (the normal case) we can be more
                    // efficient because we don't need alpha information.
                    val bitmapType =
                        if (root.uniqueDrawingId == rootsDetector.lastRootIds.firstOrNull())
                            BitmapType.RGB_565 else BitmapType.ABGR_8888
                    root.takeScreenshot(state.screenshotSettings.scale, bitmapType)
                        ?.toByteArray()
                        ?.compress()
                        ?.let { os.write(it) }
                }
            }
        }
        var task: TimerTask? = null
        lateinit var callback: Runnable
        callback = Runnable {
            task?.cancel()
            task = null
            doCapture()
            task = timerTask {
                if (!stop) {
                    doCapture()
                }
            }
            // If another frame comes in while the listener is running it seems we won't be able to
            // re-register the listener in time to capture it. In order to be sure we capture the
            // end state of an animation, schedule another update for a little bit in the future.
            timer.schedule(task, 500L)

            if (!stop) {
                root.viewTreeObserver.registerFrameCommitCallback(callback)
            }
        }
        root.viewTreeObserver.registerFrameCommitCallback(callback)
        return AutoCloseable {
            root.viewTreeObserver.unregisterFrameCommitCallback(callback)
            stop = true
        }

    }

    private fun executeCapture(command: Runnable, os: ByteArrayOutputStream, root: View) {
        var snapshotRequest: SnapshotRequest?
        var context: CaptureContext
        var screenshotSettings: ScreenshotSettings
        synchronized(stateLock) {
            snapshotRequest = state.snapshotRequests[root.uniqueDrawingId]
            if (snapshotRequest?.state?.compareAndSet(
                    SnapshotRequest.State.NEW, SnapshotRequest.State.PROCESSING
                ) != true
            ) {
                snapshotRequest = null
            }
            screenshotSettings = if (snapshotRequest != null) {
                ScreenshotSettings(Screenshot.Type.SKP)
            } else {
                state.screenshotSettings
            }
            // We might get some lingering captures even though we already finished
            // listening earlier (this would be indicated by no context). Just abort
            // early in that case.
            // Note: We copy the context instead of returning it directly, to avoid rare
            // but potential threading issues as other threads can modify the context, e.g.
            // handling the stop fetch command.
            context = state.contextMap[root.uniqueDrawingId]?.copy() ?: return
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
        run {
            // Prepare and send LayoutEvent
            // Triggers image fetch into `os`
            // We always have to do this even if we don't use the bytes it gives us,
            // because otherwise an internal queue backs up
            command.run()

            // If we have a snapshot request, we can remove it from the request map now that
            // it's no longer needed to indicate that SKPs need to be collected.
            if (snapshotRequest != null) {
                state.snapshotRequests.remove(root.uniqueDrawingId)
            }

            // This root is no longer visible. Ignore this update.
            if (!rootsDetector.lastRootIds.contains(root.uniqueDrawingId)) {
                return@run
            }
            sendLayoutEvent(root, context, screenshotSettings, os, snapshotResponse)
        }
        if (snapshotResponse != null || context.isLastCapture) {
            sendAllPropertiesEvent(root, snapshotResponse)

            // Send the updated fold state, in case we haven't been sending it continuously.
            foldSupport?.sendFoldStateEventNow()
        }
        snapshotResponse?.let { snapshotRequest?.result?.complete(it.build()) }
        return
    }

    private fun sendAllPropertiesEvent(
        root: View,
        snapshotResponse: LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.Builder?
    ) {
        // Prepare and send PropertiesEvent
        // We get here either if the client requested a one-time snapshot of the layout
        // or if the client just stopped an in-progress fetch. Collect and send all
        // properties, so that the user can continue to explore all values in the UI and
        // they will match exactly the layout at this moment in time.

        val allViews = ThreadUtils.runOnMainThread {
            root.flatten().toList()
        }.get(10, TimeUnit.SECONDS) ?: throw TimeoutException()
        val stringTable = StringTable()
        val propertyGroups = allViews.map { it.createPropertyGroup(stringTable) }
        val properties = PropertiesEvent.newBuilder().apply {
            rootId = root.uniqueDrawingId
            addAllPropertyGroups(propertyGroups)
            addAllStrings(stringTable.toStringEntries())
        }.build()
        if (snapshotResponse != null) {
            snapshotResponse.properties = properties
        } else {
            connection.sendEvent {
                propertiesEvent = properties
            }
        }
    }

    private fun sendLayoutEvent(
        root: View,
        context: CaptureContext,
        screenshotSettings: ScreenshotSettings,
        os: ByteArrayOutputStream,
        snapshotResponse: LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.Builder?
    ) {
        val screenshot = ByteString.copyFrom(os.toByteArray())

        os.reset() // Clear stream, ready for next frame
        checkpoint = ProgressCheckpoint.SCREENSHOT_CAPTURED
        if (context.isLastCapture) {
            context.shutdown()
        }

        val stringTable = StringTable()
        val appContext = root.createAppContext(stringTable)
        val configuration = root.createConfiguration(stringTable)

        val (rootView, rootOffset) = ThreadUtils.runOnMainThread {
            val rootView = root.toNode(stringTable)
            val rootOffset = IntArray(2)
            root.getLocationInSurface(rootOffset)

            (rootView to rootOffset)
        }.get()

        checkpoint = ProgressCheckpoint.VIEW_HIERARCHY_CAPTURED
        val layout =
            createLayoutMessage(
                stringTable,
                appContext,
                configuration,
                rootView,
                rootOffset,
                screenshotSettings,
                screenshot
            )
        if (snapshotResponse != null) {
            snapshotResponse.layout = layout
        } else {
            checkpoint = ProgressCheckpoint.RESPONSE_SENT
            connection.sendEvent {
                layoutEvent = layout
            }
        }
    }

    private fun createLayoutMessage(
        stringTable: StringTable,
        appContext: LayoutInspectorViewProtocol.AppContext,
        configuration: Configuration,
        rootView: LayoutInspectorViewProtocol.ViewNode,
        rootOffset: IntArray,
        screenshotSettings: ScreenshotSettings,
        screenshot: ByteString?
    ) = LayoutEvent.newBuilder().apply {
        addAllStrings(stringTable.toStringEntries())
        if (appContext != previousContext || configuration != previousConfig) {
            previousConfig = configuration
            previousContext = appContext
            this.configuration = configuration
            this.appContext = appContext
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

    private fun handleStartFetchCommand(
        startFetchCommand: StartFetchCommand,
        callback: CommandCallback
    ) {
        checkpoint = ProgressCheckpoint.START_RECEIVED
        forceStopAllCaptures()

        synchronized(stateLock) {
            state.fetchContinuously = startFetchCommand.continuous
            if (!startFetchCommand.continuous) {
                state.screenshotSettings =
                    ScreenshotSettings(Screenshot.Type.SKP, state.screenshotSettings.scale)
            }
        }

        if (startFetchCommand.continuous) {
            rootsDetector.start()
        }
        else {
            // We may be getting here after a previous start / stop flow
            rootsDetector.reset()
        }
        try {
            // Since the start command is sent right after we set the debug system properties, which
            // cause an activity restart, it's possible that the activity will still be restarting
            // at this point and we won't find any root views. Retry a few times until we do.
            var tries = 0
            while (tries++ < MAX_START_FETCH_RETRIES) {
                val result = ThreadUtils.runOnMainThread {
                    val rootViews = getRootViews()
                    if (rootViews.isEmpty()) {
                        false
                    } else {
                        for (root in rootViews) {
                            startCapturing(root)
                        }
                        foldSupport?.initialize(rootViews.first().context)
                        true
                    }
                }.get()
                when {
                    result -> break
                    tries == MAX_START_FETCH_RETRIES -> throw Exception(noRootViews())
                    else -> Thread.sleep(300)
                }
            }
        }
        catch (exception: Exception) {
            Log.w("layinsp", "Error during startCapturing", exception)
            callback.reply {
                startFetchResponse = StartFetchResponse.newBuilder().apply {
                    error = exception.cause?.message ?: "Unknown error"
                    code = exception.cause?.errorCode
                }.build()
            }
            return
        }
        callback.reply {
            startFetchResponse = StartFetchResponse.getDefaultInstance()
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
            updateAllViewChangeNotifications()
            ThreadUtils.runOnMainThread {
                for (rootView in getRootViews()) {
                    rootView.invalidate()
                }
            }
        }
    }

    private fun handleStopFetchCommand(callback: CommandCallback) {
        state.fetchContinuously = false
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
        @Suppress("UNUSED_PARAMETER") // TODO: support bitmap
        captureSnapshotCommand: LayoutInspectorViewProtocol.CaptureSnapshotCommand,
        callback: CommandCallback
    ) {
        rootsDetector.checkRoots()
        state.snapshotRequests.clear()

        scope.launch {
            val roots = ThreadUtils.runOnMainThreadAsync { getRootViews() }.await()
            val windowSnapshots = roots.map { view ->
                SnapshotRequest().also {
                    state.snapshotRequests[view.uniqueDrawingId] = it
                }.result
            }
            // At this point we need to switch to capturing SKPs if we aren't already.
            updateAllViewChangeNotifications()
            ThreadUtils.runOnMainThread { roots.forEach { it.invalidate() } }
            val reply = LayoutInspectorViewProtocol.CaptureSnapshotResponse.newBuilder().apply {
                windowRoots = WindowRootsEvent.newBuilder().apply {
                    addAllIds(roots.map { it.uniqueDrawingId })
                }.build()
                addAllWindowSnapshots(windowSnapshots.awaitAll())
            }.build()
            // And now we need to switch back to bitmaps, if we were before.
            updateAllViewChangeNotifications()
            callback.reply {
                captureSnapshotResponse = reply
            }
        }
    }
}

private fun Inspector.CommandCallback.reply(initResponse: Response.Builder.() -> Unit) {
    val response = Response.newBuilder()
    response.initResponse()
    reply(response.build().toByteArray())
}

fun Connection.sendEvent(init: Event.Builder.() -> Unit) {
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
