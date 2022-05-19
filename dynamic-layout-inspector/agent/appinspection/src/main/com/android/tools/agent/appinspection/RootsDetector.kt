/*
 * Copyright (C) 2022 The Android Open Source Project
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
import androidx.inspection.Connection
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A class which provides [checkRoots] for detecting root view changes since a previous check.
 * Any time roots or added or removed, this class will generate and send a [WindowRootsEvent] and
 * call the provided [onRootsChanged] callback.
 *
 * While the framework informs us about most screen changes, sometimes a dialog can close
 * without us being informed, so [start] is provided to spin up a background thread that checks
 * occasionally. So even though [checkRoots] can be called directly, we still have a backup to
 * handle updates that the system doesn't tell us about.
 */
class RootsDetector(
    private val connection: Connection,
    private val onRootsChanged: (List<Long>, List<Long>, Map<Long, View>) -> Unit,
    private val setCheckpoint: (ProgressCheckpoint) -> Unit
) {
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
     * called, sends a [WindowRootsEvent] to the host to inform the host, and calls
     * [onRootsChanged] with any changes.
     */
    @Synchronized
    fun checkRoots() {
        val currRoots = getRootViewsOnMainThread()
        if (quit.get()) {
            // We're quitting and cancelled the roots request.
            return
        }

        val currRootIds = currRoots.keys
        if (lastRootIds.size != currRootIds.size || !lastRootIds.containsAll(currRootIds)) {
            val removed = lastRootIds.filter { !currRootIds.contains(it) }
            val added = currRootIds.filter { !lastRootIds.contains(it) }
            lastRootIds = currRootIds
            setCheckpoint(ProgressCheckpoint.ROOTS_EVENT_SENT)
            connection.sendEvent {
                rootsEvent = WindowRootsEvent.newBuilder().apply {
                    addAllIds(currRootIds)
                }.build()
            }
            onRootsChanged(added, removed, currRoots)
        }
    }

    private fun getRootViewsOnMainThread(): Map<Long, View> {
        while (!quit.get()) {
            try {
                return ThreadUtils.runOnMainThread {
                    getRootViews().associateBy { it.uniqueDrawingId }
                }.get(100, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                // Ignore and try again.
            }
        }
        // We'll only get here if we've already quit
        return mapOf()
    }
}

fun getRootViews(): List<View> {
    ThreadUtils.assertOnMainThread()

    val views = WindowInspector.getGlobalWindowViews()
    return views
        .filter { view -> view.visibility == View.VISIBLE && view.isAttachedToWindow }
        .sortedBy { view -> view.z }
}
