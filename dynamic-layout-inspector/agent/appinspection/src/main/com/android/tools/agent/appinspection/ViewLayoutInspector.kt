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
import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.proto.toNode
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.idea.protobuf.ByteString
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val LAYOUT_INSPECTION_ID = "layoutinspector.view.inspection"

// created by java.util.ServiceLoader
class LayoutInspectorFactory : InspectorFactory<ViewLayoutInspector>(LAYOUT_INSPECTION_ID) {
    override fun createInspector(
        connection: Connection,
        environment: InspectorEnvironment
    ) = ViewLayoutInspector(connection)
}

class ViewLayoutInspector(connection: Connection) : Inspector(connection) {

    /**
     * Context data associated with a capture of a single layout tree.
     */
    private class CaptureContext(
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

    private val contextMapLock = Any()

    /**
     * A mapping of root view IDs to data that should be accessed across multiple threads.
     */
    @GuardedBy("contextMapLock")
    private val contextMap = mutableMapOf<Long, CaptureContext>()

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = Command.parseFrom(data)
        when (command.specializedCase) {
            Command.SpecializedCase.START_FETCH_COMMAND -> handleStartFetchCommand(
                command.startFetchCommand,
                callback
            )
            Command.SpecializedCase.STOP_FETCH_COMMAND -> handleStopFetchCommand(callback)
            else -> error("Unexpected view inspector command case: ${command.specializedCase}")
        }
    }

    override fun onDispose() {
        forceStopAllCaptures()
    }

    private fun forceStopAllCaptures() {
        synchronized(contextMapLock) {
            for (context in contextMap.values) {
                context.handle.close()
            }
            contextMap.clear()
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
        for (root in getRootViews()) {
            val os = ByteArrayOutputStream()
            // We might get multiple callbacks for the same view while still processing an earlier
            // one. Let's avoid processing these in parallel to avoid confusion.
            val sequentialExecutor = Executors.newSingleThreadExecutor { r -> ThreadUtils.newThread(r) }

            val captureExecutor = Executor { command ->
                sequentialExecutor.execute {
                    var context: CaptureContext
                    synchronized(contextMapLock) {
                        // We might get some lingering captures even though we already finished
                        // listening earlier (this would be indicated by no context). Just abort
                        // early in that case.
                        context = contextMap[root.uniqueDrawingId] ?: return@execute
                        if (context.isLastCapture) {
                            contextMap.remove(root.uniqueDrawingId)
                        }
                    }

                    command.run() // Triggers image fetch into `os`
                    val screenshot = ByteString.copyFrom(os.toByteArray())
                    os.reset() // Clear stream, ready for next frame

                    if (context.isLastCapture) {
                        context.handle.close()
                    }

                    val stringTable = StringTable()
                    lateinit var rootView: ViewNode
                    run {
                        // Get layout info on the view thread, to avoid races
                        val future = CompletableFuture<Unit>()
                        root.post {
                            rootView = root.toNode(stringTable)
                            future.complete(Unit)
                        }
                        future.get()
                    }

                    connection.sendEvent {
                        layoutEvent = LayoutEvent.newBuilder().apply {
                            addAllStrings(stringTable.toStringEntries())
                            this.rootView = rootView
                            this.screenshot = Screenshot.newBuilder().apply {
                                type = Screenshot.Type.SKP
                                bytes = screenshot
                            }.build()
                        }.build()
                    }
                }
            }

            // Starting rendering captures must be called on the View thread or else it throws
            root.post {
                // Sometimes the window has become detached before we get in here,
                // so check one more time before trying to start capture.
                if (!root.isAttachedToWindow) return@post

                try {
                    synchronized(contextMapLock) {
                        val handle =
                            SkiaQWorkaround.startRenderingCommandsCapture(
                                root,
                                captureExecutor
                            ) { os }
                        if (handle != null) {
                            contextMap[root.uniqueDrawingId] =
                                CaptureContext(handle, isLastCapture = (!startFetchCommand.continuous))
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
        }
    }

    private fun handleStopFetchCommand(callback: CommandCallback) {
        callback.reply {
            stopFetchResponse = StopFetchResponse.getDefaultInstance()
        }

        synchronized(contextMapLock) {
            for (context in contextMap.values) {
                context.isLastCapture = true
            }
        }
    }
}

private fun getRootViews(): List<View> {
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
