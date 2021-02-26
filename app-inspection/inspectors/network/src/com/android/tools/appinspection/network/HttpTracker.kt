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
package com.android.tools.appinspection.network

import com.android.tools.appinspection.network.utils.ByteBatcher
import java.io.InputStream
import java.io.OutputStream

/**
 * This is the factory for the [HttpConnectionTracker] instances
 */
object HttpTracker {

    /**
     * Starts tracking a HTTP request
     *
     * @param url       the request URL
     * @param callstack optional callstack, if null the code location is not tracked
     * @return an object implementing [HttpConnectionTracker] that associated with a
     * particular HTTP request
     */
    fun trackConnection(url: String, callstack: Array<StackTraceElement>): HttpConnectionTracker {
        return Connection(url, callstack)
    }

    /**
     * Wraps an InputStream to enable the network profiler capturing of response body
     */
    private class InputStreamTracker(
        private val wrapped: InputStream,
        private val connectionTracker: Connection
    ) : InputStream() {

        private var firstRead = true
        private val byteBatcher = ByteBatcher { bytes, validBytesLength ->
            reportBytes(connectionTracker.id, bytes, validBytesLength)
        }

        override fun available(): Int {
            return wrapped.available()
        }

        override fun markSupported(): Boolean {
            return wrapped.markSupported()
        }

        override fun mark(readLimit: Int) {
            wrapped.mark(readLimit)
        }

        override fun reset() {
            wrapped.reset()
        }

        override fun close() {
            wrapped.close()
            byteBatcher.flush()
            onClose(connectionTracker.id)
        }

        override fun read(buffer: ByteArray): Int {
            return read(buffer, 0, buffer.size)
        }

        override fun read(): Int {
            if (firstRead) {
                onReadBegin(connectionTracker.id)
                firstRead = false
            }
            val b = wrapped.read()
            byteBatcher.addByte(b)
            connectionTracker.trackThread()
            return b
        }

        override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int {
            if (firstRead) {
                onReadBegin(connectionTracker.id)
                firstRead = false
            }
            val bytesRead = wrapped.read(buffer, byteOffset, byteCount)
            byteBatcher.addBytes(buffer, byteOffset, bytesRead)
            connectionTracker.trackThread()
            return bytesRead
        }

        override fun skip(byteCount: Long): Long {
            if (firstRead) {
                onReadBegin(connectionTracker.id)
                firstRead = false
            }
            connectionTracker.trackThread()
            return wrapped.skip(byteCount)
        }

        private external fun onClose(id: Long)
        private external fun onReadBegin(id: Long)
        private external fun reportBytes(id: Long, bytes: ByteArray, len: Int)
    }

    /**
     * Wraps an OutputStream to enable the network profiler capturing of request body
     */
    private class OutputStreamTracker(
        private val wrapped: OutputStream,
        private val connectionTracker: Connection
    ) : OutputStream() {

        private var firstWrite = true
        private val byteBatcher = ByteBatcher { bytes, validBytesLength ->
            reportBytes(connectionTracker.id, bytes, validBytesLength)
        }

        override fun close() {
            wrapped.close()
            byteBatcher.flush()
            onClose(connectionTracker.id)
        }

        override fun flush() {
            wrapped.flush()
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (firstWrite) {
                onWriteBegin(connectionTracker.id)
                firstWrite = false
            }
            wrapped.write(buffer, offset, length)
            byteBatcher.addBytes(buffer, offset, length)
            connectionTracker.trackThread()
        }

        override fun write(oneByte: Int) {
            if (firstWrite) {
                onWriteBegin(connectionTracker.id)
                firstWrite = false
            }
            wrapped.write(oneByte)
            byteBatcher.addByte(oneByte)
            connectionTracker.trackThread()
        }

        override fun write(buffer: ByteArray) {
            write(buffer, 0, buffer.size)
        }

        private external fun onClose(id: Long)
        private external fun onWriteBegin(id: Long)
        private external fun reportBytes(id: Long, bytes: ByteArray, len: Int)
    }

    /**
     * This is the concrete AndroidStudio implementation of the public HTTP tracking interface.
     * We're passing the HTTP events and content to the AndroidStudio network profiler.
     *
     * Note that the HTTP stacks using [HttpConnectionTracker] should not care or know about
     * the details of the implementation of the interface.
     */
    private class Connection(url: String, callstack: Array<StackTraceElement>) :
        HttpConnectionTracker {

        val id: Long
        private val url: String
        private val callstack: String
        private var lastThread: Thread? = null

        init {
            id = nextId()
            this.url = url
            val s = StringBuilder()
            for (e in callstack) {
                s.append(e)
                s.append('\n')
            }
            this.callstack = s.toString()
        }

        override fun disconnect() {
            onDisconnect(id)
        }

        override fun error(message: String) {
            onError(id, message)
        }

        override fun trackRequestBody(stream: OutputStream): OutputStream {
            return OutputStreamTracker(stream, this)
        }

        override fun trackRequest(method: String, fields: Map<String, List<String>>) {
            val s = StringBuilder()
            for ((key, value) in fields) {
                s.append(key).append(" = ")
                for (`val` in value) {
                    s.append(`val`).append("; ")
                }
                s.append('\n')
            }
            onRequest(id, url, callstack, method, s.toString())
            trackThread()
        }

        override fun trackResponse(response: String, fields: Map<String, List<String>>) {
            val s = StringBuilder()
            for ((key, value) in fields) {
                s.append(key).append(" = ")
                for (`val` in value) {
                    s.append(`val`).append("; ")
                }
                s.append('\n')
            }
            onResponse(id, response, s.toString())
            trackThread()
        }

        override fun trackResponseBody(stream: InputStream): InputStream {
            return InputStreamTracker(stream, this)
        }

        fun trackThread() {
            val thread = Thread.currentThread()
            if (thread !== lastThread) {
                trackThread(id, thread.name, thread.id)
                lastThread = thread
            }
        }

        private external fun nextId(): Long
        private external fun trackThread(id: Long, theadName: String, threadId: Long)
        private external fun onRequest(
            id: Long,
            url: String,
            callstack: String,
            method: String?,
            fields: String
        )

        private external fun onResponse(id: Long, response: String?, fields: String)
        private external fun onDisconnect(id: Long)
        private external fun onError(id: Long, status: String?)
    }
}
