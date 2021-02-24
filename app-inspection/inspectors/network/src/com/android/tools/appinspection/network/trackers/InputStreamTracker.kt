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

package com.android.tools.appinspection.network.trackers

import com.android.tools.appinspection.network.reporters.StreamReporter
import java.io.InputStream

/**
 * Wraps an InputStream to enable the network inspector capturing of response body
 */
class InputStreamTracker(
    private val myWrapped: InputStream,
    private val reporter: StreamReporter
) : InputStream() {

    override fun available(): Int {
        return myWrapped.available()
    }

    override fun markSupported(): Boolean {
        return myWrapped.markSupported()
    }

    override fun mark(readLimit: Int) {
        myWrapped.mark(readLimit)
    }

    override fun reset() {
        myWrapped.reset()
    }

    override fun close() {
        myWrapped.close()
        reporter.onStreamClose()
    }

    override fun read(buffer: ByteArray): Int {
        return read(buffer, 0, buffer.size)
    }

    override fun read(): Int {
        val b = myWrapped.read()
        reporter.addOneByte(b)
        reporter.reportCurrentThread()
        return b
    }

    override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int {
        val bytesRead = myWrapped.read(buffer, byteOffset, byteCount)
        // bytesRead is -1 if we've read to stream's end.
        if (bytesRead > 0) {
            reporter.addBytes(buffer, byteOffset, bytesRead)
        }
        reporter.reportCurrentThread()
        return bytesRead
    }

    override fun skip(byteCount: Long): Long {
        reporter.reportCurrentThread()
        return myWrapped.skip(byteCount)
    }
}
