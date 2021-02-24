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
import java.io.OutputStream

/**
 * Wraps an OutputStream to enable the network inspector capturing of request body
 */
class OutputStreamTracker(
    private val myWrapped: OutputStream,
    private val reporter: StreamReporter
) : OutputStream() {

    override fun close() {
        myWrapped.close()
        reporter.onStreamClose()
    }

    override fun flush() {
        myWrapped.flush()
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        myWrapped.write(buffer, offset, length)
        reporter.addBytes(buffer, offset, length)
        reporter.reportCurrentThread()
    }

    override fun write(oneByte: Int) {
        myWrapped.write(oneByte)
        reporter.addOneByte(oneByte)
        reporter.reportCurrentThread()
    }

    override fun write(buffer: ByteArray) {
        write(buffer, 0, buffer.size)
    }
}
