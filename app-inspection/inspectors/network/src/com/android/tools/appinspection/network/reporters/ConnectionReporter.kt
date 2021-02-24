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
import com.android.tools.appinspection.network.utils.ConnectionIdGenerator
import com.android.tools.appinspection.network.utils.sendHttpConnectionEvent
import studio.network.inspection.NetworkInspectorProtocol

/**
 * A class that is used to report connection related activity to Studio such as
 * making requests or receiving responses.
 */
interface ConnectionReporter : ThreadReporter {

    fun onRequest(
        url: String,
        callstack: String,
        method: String,
        fields: String
    )

    fun onResponse(fields: String)

    fun onError(status: String)

    fun createInputStreamReporter(): StreamReporter

    fun createOutputStreamReporter(): StreamReporter

    companion object {

        fun createConnectionTracker(connection: Connection): ConnectionReporter =
            ConnectionReporterImpl(connection)
    }
}

private class ConnectionReporterImpl(
    private val connection: Connection
) : ConnectionReporter, ThreadReporter {

    private val connectionId = ConnectionIdGenerator.nextId()
    private val threadReporter = ThreadReporter.createThreadReporter(connection, connectionId)

    override fun reportCurrentThread() {
        threadReporter.reportCurrentThread()
    }

    override fun createInputStreamReporter(): StreamReporter {
        return InputStreamReporterImpl(connection, connectionId, threadReporter)
    }

    override fun createOutputStreamReporter(): StreamReporter {
        return OutputStreamReporterImpl(connection, connectionId, threadReporter)
    }

    override fun onRequest(
        url: String,
        callstack: String,
        method: String,
        fields: String
    ) {
        connection.sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setHttpRequestStarted(
                    NetworkInspectorProtocol.HttpConnectionEvent.RequestStarted.newBuilder()
                        .setUrl(url)
                        .setTrace(callstack)
                        .setMethod(method)
                        .setFields(fields)
                )
                .setConnectionId(connectionId)
        )
    }

    override fun onResponse(fields: String) {
        connection.sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setHttpResponseStarted(
                    NetworkInspectorProtocol.HttpConnectionEvent.ResponseStarted.newBuilder()
                        .setFields(fields)
                )
                .setConnectionId(connectionId)
        )
    }

    override fun onError(status: String) {
        connection.sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setHttpClosed(
                    NetworkInspectorProtocol.HttpConnectionEvent.Closed.newBuilder()
                        .setCompleted(false)
                )
                .setConnectionId(connectionId)
        )
    }
}
