/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.device.internal.adb

import com.android.tools.device.internal.ScopedThreadNameRunnable
import com.google.common.primitives.Bytes
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Pipe
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ForkJoinPool

class PipeAdbServer : Closeable {
    private val inPipe = Pipe.open()
    private val outPipe = Pipe.open()

    val commandSink: WritableByteChannel = inPipe.sink()
    private val commandSource = inPipe.source()

    val responseSource: ReadableByteChannel = outPipe.source()
    private val responseSink = outPipe.sink()

    val commandBuffer = CopyOnWriteArrayList<Byte>()

    init {
        ForkJoinPool.commonPool().submit(
                ScopedThreadNameRunnable.wrap(
                        Runnable {
                            val dst = ByteBuffer.allocate(8)
                            while (true) {
                                try {
                                    commandSource.read(dst)
                                    dst.flip()
                                    commandBuffer.addAll(dst)
                                    dst.rewind()
                                } catch (e: IOException) {
                                    return@Runnable
                                }
                            }
                        },
                        "adb-pipe-reader"))
    }

    private fun WritableByteChannel.writeFully(buf: ByteBuffer) {
        while (buf.position() != buf.limit()) {
            write(buf)
        }
    }

    fun respondWith(data: ByteArray) {
        outPipe.sink().writeFully(ByteBuffer.wrap(data))
    }

    fun waitForCommand(command: String) {
        do {
            val receivedCommand = Bytes.toArray(commandBuffer).toString(Charsets.UTF_8)
        } while (receivedCommand != command)
    }

    override fun close() {
        commandSource.close()
        commandSink.close()

        responseSource.close()
        responseSink.close()
    }
}

private fun MutableList<Byte>.addAll(elements: ByteBuffer) {
    while (elements.hasRemaining()) {
        add(elements.get())
    }
}
