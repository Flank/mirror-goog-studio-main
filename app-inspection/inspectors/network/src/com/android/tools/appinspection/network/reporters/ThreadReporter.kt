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

package com.android.tools.appinspection.network.reporters

import androidx.inspection.Connection
import com.android.tools.appinspection.network.utils.sendHttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol

/**
 * A reporter used for sending and reporting thread related events.
 */
interface ThreadReporter {

    /**
     * Reports the current thread's call frames to Studio.
     */
    fun reportCurrentThread()

    companion object {

        fun createThreadReporter(connection: Connection, connectionId: Long): ThreadReporter =
            ThreadReporterImpl(connection, connectionId)
    }
}

private class ThreadReporterImpl(
    private val connection: Connection,
    private val connectionId: Long
) : ThreadReporter {

    private var lastThread: Thread? = null

    override fun reportCurrentThread() {
        val thread = Thread.currentThread()
        if (thread !== lastThread) {
            connection.sendHttpConnectionEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                    .setHttpThread(
                        NetworkInspectorProtocol.HttpConnectionEvent.ThreadData.newBuilder()
                            .setThreadId(thread.id)
                            .setThreadName(thread.name)
                    )
                    .setConnectionId(connectionId)
            )
            lastThread = thread
        }
    }
}
