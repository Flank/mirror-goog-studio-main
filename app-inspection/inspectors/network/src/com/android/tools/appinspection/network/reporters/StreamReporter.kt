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
import com.android.tools.idea.protobuf.ByteString
import studio.network.inspection.NetworkInspectorProtocol

/**
 * The initial capacity of the buffer that stores payload data.
 * It is automatically expanded when capacity is reached.
 */
const val DEFAULT_THRESHOLD = 1024

/**
 * A class that reports on [InputStream] and [OutputStream]. It
 * records the payload that is sent/received in a temporary buffer
 * before reporting it to Studio.
 */
sealed class StreamReporter(
    private val connection: Connection,
    threadReporter: ThreadReporter,
    private val connectionId: Long
) : ThreadReporter by threadReporter {

    private val buffer = ByteString.newOutput(DEFAULT_THRESHOLD)
    private var isClosed = false

    protected abstract fun onClosed(data: ByteString)

    fun addOneByte(byte: Int) {
        buffer.write(byte)
    }

    fun addBytes(bytes: ByteArray, offset: Int, len: Int) {
        buffer.write(bytes, offset, len)
    }

    fun onStreamClose() {
        // This is to prevent the double reporting of stream closed events
        // because this is reachable by both calling disconnect() on the
        // HttpUrlConnection, and calling close() on the stream.
        if (!isClosed) {
            isClosed = true
            onClosed(buffer.toByteString())
        }
    }

    protected fun sendHttpConnectionEvent(builder: NetworkInspectorProtocol.HttpConnectionEvent.Builder) {
        connection.sendHttpConnectionEvent(
            builder.setConnectionId(connectionId)
        )
    }
}

class InputStreamReporterImpl(
    connection: Connection,
    connectionId: Long,
    threadReporter: ThreadReporter
) : StreamReporter(connection, threadReporter, connectionId) {

    override fun onClosed(data: ByteString) {
        sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setResponsePayload(
                    NetworkInspectorProtocol.HttpConnectionEvent.Payload.newBuilder()
                        .setPayload(data)
                )
        )
        sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setHttpResponseCompleted(
                    NetworkInspectorProtocol.HttpConnectionEvent.ResponseCompleted.getDefaultInstance()
                )
        )
        sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setHttpClosed(
                    NetworkInspectorProtocol.HttpConnectionEvent.Closed.newBuilder()
                        .setCompleted(true)
                )
        )
    }
}

class OutputStreamReporterImpl(
    connection: Connection,
    connectionId: Long,
    threadReporter: ThreadReporter
) : StreamReporter(connection, threadReporter, connectionId) {

    override fun onClosed(data: ByteString) {
        sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setRequestPayload(
                    NetworkInspectorProtocol.HttpConnectionEvent.Payload.newBuilder()
                        .setPayload(data)
                )
        )
        sendHttpConnectionEvent(
            NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
                .setHttpRequestCompleted(
                    NetworkInspectorProtocol.HttpConnectionEvent.RequestCompleted.getDefaultInstance()
                )
        )
    }
}
