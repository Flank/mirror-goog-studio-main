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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.AdbInputChannelSlice
import com.android.adblib.AdbOutputChannel
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.forwardTo
import com.android.adblib.skipRemaining
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.copy
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD_SET
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.EOFException

/**
 * Serialize the [DdmsChunkView] into an [AdbOutputChannel].
 *
 * @throws IllegalArgumentException if [DdmsChunkView.data] does not contain exactly
 * [DdmsChunkView.length] bytes
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
internal suspend fun DdmsChunkView.writeToChannel(
    channel: AdbOutputChannel,
    workBuffer: ResizableBuffer = ResizableBuffer()
) {
    workBuffer.clear()
    workBuffer.order(DDMS_CHUNK_BYTE_ORDER)

    // Write header
    workBuffer.appendInt(type)
    workBuffer.appendInt(length)
    channel.writeExactly(workBuffer.forChannelWrite())

    // Write data
    val byteCount = data.forwardTo(channel, workBuffer)
    data.rewind()
    checkChunkLength(byteCount)
}

/**
 * Returns an in-memory copy of this [DdmsChunkView].
 *
 * @throws IllegalArgumentException if [DdmsChunkView.data] does not contain exactly
 * [DdmsChunkView.length] bytes
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
internal suspend fun DdmsChunkView.clone(
    workBuffer: ResizableBuffer = ResizableBuffer()
): DdmsChunkView {
    val ddmsChunk = MutableDdmsChunk()

    // Copy header
    ddmsChunk.length = length
    ddmsChunk.type = type

    // Copy data to "workBuffer"
    workBuffer.clear()
    val dataCopy = ByteBufferAdbOutputChannel(workBuffer)
    val byteCount = data.forwardTo(dataCopy)
    data.rewind()
    ddmsChunk.checkChunkLength(byteCount)

    // Make a copy into our own ByteBuffer
    val bufferCopy = workBuffer.forChannelWrite().copy()

    // Create rewindable data channel
    ddmsChunk.data = AdbBufferedInputChannel.forByteBuffer(bufferCopy)

    return ddmsChunk
}

/**
 * Provides a view of a [JdwpPacketView] as a "DDMS" packet. A "DDMS" packet is a
 * "JDWP" packet that contains one or more [chunks][DdmsChunkView].
 *
 * Each chunk starts with an 8 bytes header followed by chunk specific data
 *
 *  * chunk type (4 bytes)
 *  * chunk length (4 bytes)
 *  * chunk data (variable, specific to the chunk type)
 */
internal fun JdwpPacketView.ddmsChunks(
    workBuffer: ResizableBuffer = ResizableBuffer()
): Flow<DdmsChunkView> {
    val jdwpPacketView = this
    return flow {
        if (!isReply && !isCommand(DDMS_CMD_SET, DDMS_CMD)) {
            throw IllegalArgumentException("JDWP packet is not a DDMS command packet (and is not a reply packet)")
        }

        jdwpPacketView.data.rewind()
        workBuffer.clear()
        workBuffer.order(DDMS_CHUNK_BYTE_ORDER)
        while (true) {
            try {
                jdwpPacketView.data.readExactly(workBuffer.forChannelRead(DDMS_CHUNK_HEADER_LENGTH))
            } catch (e: EOFException) {
                // Regular exit: there are no more chunks to be read
                break
            }
            val data = workBuffer.afterChannelRead()

            // Prepare chunk source
            val chunk = MutableDdmsChunk().apply {
                this.type = data.getInt()
                this.length = data.getInt()
                val slice = AdbInputChannelSlice(jdwpPacketView.data, this.length)
                this.data = AdbBufferedInputChannel.forInputChannel(slice)
            }

            // Emit it to collector
            emit(chunk)

            // Ensure we consume all bytes from the chunk data in case the collector did not
            // do anything with it
            chunk.data.skipRemaining(workBuffer)
        }
    }
}

private fun DdmsChunkView.checkChunkLength(byteCount: Int) {
    val expectedByteCount = length
    if (byteCount != expectedByteCount) {
        throw IllegalArgumentException(
            "DDMS packet should contain $expectedByteCount " +
                    "bytes but contains $byteCount bytes instead"
        )
    }
}
