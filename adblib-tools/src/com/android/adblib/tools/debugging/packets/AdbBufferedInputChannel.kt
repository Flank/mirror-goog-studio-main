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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.AdbInputChannel
import com.android.adblib.ByteBufferAdbInputChannel
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * A [AdbBufferedInputChannel] is an [AdbInputChannel] that supports a [rewind] operation
 */
interface AdbBufferedInputChannel : AdbInputChannel {

    /**
     * Rewind this [AdbBufferedInputChannel] to the beginning, so that [read] operations can
     * be executed again.
     */
    fun rewind()

    /**
     * Similar to [rewind] but gives implementors a hint this is the last time a [rewind]
     * operation is invoked on this instance, allowing implementors to stop buffering
     * as an optimization. After this call, it is legal to [read] the contents of this
     * [AdbBufferedInputChannel] until EOF, but it is illegal to call [rewind] or [finalRewind] again.
     */
    fun finalRewind() {
        rewind()
    }

    companion object {

        /**
         * The empty [AdbBufferedInputChannel]
         */
        fun empty(): AdbBufferedInputChannel = Empty

        /**
         * A [AdbBufferedInputChannel] that wraps a [ByteBuffer], from [ByteBuffer.position]
         * to [ByteBuffer.limit]. [rewind] resets the [ByteBuffer.position] to its original
         * value.
         */
        fun forByteBuffer(buffer: ByteBuffer): AdbBufferedInputChannel {
            return ForByteBuffer(buffer)
        }

        /**
         * A [AdbBufferedInputChannel] that wraps an [AdbInputChannel], keeping data read
         * from the channel in-memory to support [rewind]. The returned object also
         * supports [ForInputChannel.finalRewind] to stop the in-memory
         * buffering behavior, as an optimization for the final reader.
         */
        fun forInputChannel(input: AdbInputChannel): AdbBufferedInputChannel {
            return ForInputChannel(input)
        }

        /**
         * The empty [AdbBufferedInputChannel]
         */
        private object Empty : AdbBufferedInputChannel {

            override fun rewind() {
                // Nothing to do
            }

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                return -1
            }

            override fun close() {
                // Nothing to do
            }
        }

        private class ForByteBuffer(private val buffer: ByteBuffer) : AdbBufferedInputChannel {

            private val rewindPosition = buffer.position()
            private val input = ByteBufferAdbInputChannel(buffer)

            override fun rewind() {
                buffer.position(rewindPosition)
            }

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                return input.read(buffer, timeout, unit)
            }

            override fun close() {
                input.close()
            }
        }

        /**
         * A [AdbBufferedInputChannel] that wraps an [AdbInputChannel] and buffers data in memory
         * to allow [rewinding][rewind].
         */
        private class ForInputChannel(private val input: AdbInputChannel) : AdbBufferedInputChannel {

            /**
             * Buffer contains previously read data from [0, limit].
             * Buffer contains data that needs to be "replayed" from [position, limit]
             */
            private var _bufferedData: ByteBuffer? = null

            private val bufferedData: ByteBuffer
                get() {
                    return _bufferedData ?: run {
                        ByteBuffer.allocate(16).limit(0).also {
                            _bufferedData = it
                        }
                    }
                }


            /**
             * Whether buffering in memory is enabled or not. When buffering is disabled,
             * [rewinding][rewind] throws [IllegalStateException]
             */
            private var buffering: Boolean = true

            override fun rewind() {
                if (!buffering) {
                    throw IllegalStateException("Rewinding is not supported after finalRewind has been invoked")
                }
                _bufferedData?.position(0)
            }

            override fun finalRewind() {
                if (!buffering) {
                    throw IllegalStateException("finalRewind can only be invoked once")
                }
                rewind()
                buffering = false
            }

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                // Read from bufferedData first if available
                if ((_bufferedData?.remaining() ?: 0) > 0) {
                    val count = min(bufferedData.remaining(), buffer.remaining())
                    val slice = bufferedData.slice()
                    slice.limit(slice.position() + count)
                    buffer.put(slice)
                    bufferedData.position(bufferedData.position() + count)
                    return count
                }

                // Read from underlying channel and append data to internal buffer
                val count = input.read(buffer, timeout, unit)
                if (count > 0 && buffering) {
                    // Buffer has received data from [position - count, position],
                    // create a slice for that range so we can buffer it.
                    val bufferSlice = buffer.duplicate()
                    bufferSlice.limit(buffer.position())
                    bufferSlice.position(buffer.position() - count)
                    assert(bufferSlice.remaining() == count)

                    // bufferedData data is currently from [0, position==limit], append data
                    // to [limit, capacity] then update range to [0, newPosition==newLimit].
                    ensureRoom(count)
                    assert(bufferedData.position() == bufferedData.limit())
                    assert(bufferedData.limit() + count <= bufferedData.capacity())
                    bufferedData.limit(bufferedData.position() + count)
                    assert(bufferedData.remaining() == count)
                    bufferedData.put(bufferSlice)
                    assert(bufferedData.remaining() == 0)
                }
                return count
            }

            override fun close() {
                this.input.close()
            }

            private fun ensureRoom(count: Int) {
                // bufferedData has available room at [limit, capacity]
                val minCapacity = bufferedData.limit() + count
                val newCapacity = nextCapacity(minCapacity)
                if (newCapacity != bufferedData.capacity()) {
                    // bufferedData has data from [0, limit]
                    val newBuffer = ByteBuffer.allocate(newCapacity)
                    newBuffer.order(bufferedData.order())

                    // bufferedData has data from [0, limit]
                    assert(bufferedData.position() == bufferedData.limit())
                    bufferedData.position(0)
                    newBuffer.put(bufferedData)
                    assert(bufferedData.position() == bufferedData.limit())
                    newBuffer.position(bufferedData.limit())
                    newBuffer.limit(bufferedData.limit())
                    _bufferedData = newBuffer
                }
            }

            private fun nextCapacity(minCapacity: Int): Int {
                var newCapacity = bufferedData.capacity()
                while (newCapacity < minCapacity) {
                    newCapacity *= 2
                }
                return newCapacity
            }
        }

    }
}
