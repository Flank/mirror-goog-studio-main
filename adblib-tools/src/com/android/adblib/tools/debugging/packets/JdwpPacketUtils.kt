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
import com.android.adblib.AdbOutputChannel
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.forwardTo
import com.android.adblib.impl.channels.DEFAULT_CHANNEL_BUFFER_SIZE
import com.android.adblib.readRemaining
import com.android.adblib.utils.ResizableBuffer
import java.nio.ByteBuffer

/**
 * Serialize the [JdwpPacketView] into an [AdbOutputChannel].
 *
 * @throws IllegalArgumentException if [JdwpPacketView.payload] does not contain exactly
 * [JdwpPacketView.length] minus [JdwpPacketConstants.PACKET_HEADER_LENGTH] bytes.
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
suspend fun JdwpPacketView.writeToChannel(
    channel: AdbOutputChannel,
    workBuffer: ResizableBuffer = ResizableBuffer(DEFAULT_CHANNEL_BUFFER_SIZE)
) {
    workBuffer.clear()
    workBuffer.order(JdwpPacketConstants.PACKET_BYTE_ORDER)
    workBuffer.appendJdwpHeader(this)

    // If packet is somewhat large, write incrementally, otherwise write in a single
    // "write" operation
    if (length >= DEFAULT_CHANNEL_BUFFER_SIZE) {
        channel.writeExactly(workBuffer.forChannelWrite())
        workBuffer.clear()
        val byteCount = channel.write(payload, workBuffer)
        checkPacketLength(byteCount)
    } else {
        val byteCount = payload.readRemaining(workBuffer)
        checkPacketLength(byteCount)
        channel.writeExactly(workBuffer.afterChannelRead(0))
    }
    this.payload.rewind()
}

/**
 * Append this JDWP packet header (11 bytes) to this [ResizableBuffer].
 */
fun ResizableBuffer.appendJdwpHeader(jdwpPacketView: JdwpPacketView) {
    order(JdwpPacketConstants.PACKET_BYTE_ORDER)

    // Construct and send packet header
    // Byte [0, 3] : Length
    // Byte [4, 7] : Id
    // Byte [8, 8] : flags
    // Byte [9, 9] : cmd set   || errorCode high byte (if reply)
    // Byte [10, 10] : cmd     || errorCode low byte (if reply)
    appendInt(jdwpPacketView.length)
    appendInt(jdwpPacketView.id)
    appendByte(jdwpPacketView.flags.toByte())
    if (jdwpPacketView.isCommand) {
        appendByte(jdwpPacketView.cmdSet.toByte())
        appendByte(jdwpPacketView.cmd.toByte())
    } else {
        appendShort(jdwpPacketView.errorCode.toShort())
    }
}

/**
 * Returns an in-memory copy of this [JdwpPacketView].
 *
 * @throws IllegalArgumentException if [JdwpPacketView.payload] does not contain exactly
 * [JdwpPacketView.length] minus [JdwpPacketConstants.PACKET_HEADER_LENGTH] bytes
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
internal suspend fun JdwpPacketView.clone(
    workBuffer: ResizableBuffer = ResizableBuffer()
): MutableJdwpPacket {

    // Copy header
    workBuffer.clear()
    workBuffer.appendJdwpHeader(this)

    val mutableJdwpPacket = MutableJdwpPacket()
    mutableJdwpPacket.parseHeader(workBuffer.forChannelWrite())

    // Copy payload into our workBuffer
    workBuffer.clear()
    val copyChannel = ByteBufferAdbOutputChannel(workBuffer)
    val byteCount = copyChannel.write(this.payload)
    checkPacketLength(byteCount)

    // Make a copy into our own ByteBuffer
    val bufferCopy = workBuffer.forChannelWrite().copy()

    // Make an input channel for it
    mutableJdwpPacket.payload = AdbBufferedInputChannel.forByteBuffer(bufferCopy)

    this.payload.rewind()
    return mutableJdwpPacket
}

private fun JdwpPacketView.checkPacketLength(byteCount: Int) {
    val expectedByteCount = length - JdwpPacketConstants.PACKET_HEADER_LENGTH
    if (byteCount != expectedByteCount) {
        throw IllegalArgumentException(
            "JDWP packet should contain $expectedByteCount " +
                    "bytes but contains $byteCount bytes instead"
        )
    }
}

/**
 * Returns a [ByteBuffer] that contains a copy the contents of this [ByteBuffer], from
 * [ByteBuffer.position] to [ByteBuffer.limit]. The returned [ByteBuffer] has data
 * from [position][ByteBuffer.position] 0 to [limit][ByteBuffer.limit].
 */
internal fun ByteBuffer.copy(): ByteBuffer {
    val temp = this.duplicate()
    val result = ByteBuffer.allocate(temp.remaining())
    result.put(temp)
    result.position(0)
    result.limit(this.remaining())
    return result
}

/**
 * Writes the entire contents of [inputChannel] to this [AdbOutputChannel] and returns the
 * number of bytes written.
 */
internal suspend fun AdbOutputChannel.write(
    inputChannel: AdbInputChannel,
    workBuffer: ResizableBuffer = ResizableBuffer()
): Int {
    return inputChannel.forwardTo(this, workBuffer)
}

/**
 * Read the first 11 bytes of [buffer] into [MutableJdwpPacket.length],
 * [MutableJdwpPacket.id] and so on. The [MutableJdwpPacket.payload] field
 * is not modified.
 */
internal fun MutableJdwpPacket.parseHeader(buffer: ByteBuffer) {
    buffer.order(JdwpPacketConstants.PACKET_BYTE_ORDER)

    // Byte [0, 3] : Length
    // Byte [4, 7] : Id
    // Byte [8, 8] : flags     || (if reply) 0x80
    // Byte [9, 9] : cmd set   || (if reply) error code high byte
    // Byte [10, 10] : cmd     || (if reply) error code low byte
    length = buffer.getInt()
    id = buffer.getInt()
    flags = buffer.get().toUByte().toInt()
    if (isReply) {
        errorCode = buffer.getShort().toUShort().toInt()
    } else {
        cmdSet = buffer.get().toUByte().toInt()
        cmd = buffer.get().toUByte().toInt()
    }
}
