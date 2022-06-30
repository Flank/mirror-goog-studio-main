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

import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.forwardTo
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class DdmsChunkViewTest : AdbLibToolsTestBase() {

    @Test
    fun test_DdmsChunkView_Write() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestDdmsChunk()

        // Act
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packet.writeToChannel(output)

        // Assert: Data should be from [0, outputBuffer.position[
        var index = 0
        assertEquals(12, outputBuffer.position)

        // Type
        assertEquals('R'.code.toByte(), outputBuffer[index++])
        assertEquals('E'.code.toByte(), outputBuffer[index++])
        assertEquals('A'.code.toByte(), outputBuffer[index++])
        assertEquals('Q'.code.toByte(), outputBuffer[index++])

        // Length
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(4.toByte(), outputBuffer[index++])

        // Contents
        assertEquals(128.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(255.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index])
    }

    @Test
    fun test_DdmsChunkView_Clone() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestDdmsChunk()

        // Act
        val clone = packet.clone()

        // Assert: header
        assertEquals(packet.type, clone.type)
        assertEquals(packet.length, clone.length)

        // Assert: data
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packet.data.forwardTo(output)

        var index = 0
        assertEquals(128.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(255.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index])

        //TODO: assertTrue(clone.data.rewind())
    }

    @Test
    fun test_JdwpPacketView_ddmsChunks_Works() = runBlockingWithTimeout {
        // Prepare
        val jdwpPacket = createJdwpPacketWithThreeDdmsChunks()

        // Act
        val chunks = jdwpPacket.ddmsChunks().toList()

        // Assert
        assertEquals(3, chunks.size)

        assertEquals(DdmsChunkTypes.REAQ, chunks[0].type)
        assertEquals(4, chunks[0].length)

        assertEquals(DdmsChunkTypes.APNM, chunks[1].type)
        assertEquals(0, chunks[1].length)

        assertEquals(DdmsChunkTypes.HELO, chunks[2].type)
        assertEquals(8, chunks[2].length)
    }

    @Test
    fun test_JdwpPacketView_ddmsChunks_FlowIsRepeatable() = runBlockingWithTimeout {
        // Prepare
        val jdwpPacket = createJdwpPacketWithThreeDdmsChunks()
        jdwpPacket.ddmsChunks().toList()

        // Act
        val chunks = jdwpPacket.ddmsChunks().toList()

        // Assert
        assertEquals(3, chunks.size)

        assertEquals(DdmsChunkTypes.REAQ, chunks[0].type)
        assertEquals(4, chunks[0].length)

        assertEquals(DdmsChunkTypes.APNM, chunks[1].type)
        assertEquals(0, chunks[1].length)

        assertEquals(DdmsChunkTypes.HELO, chunks[2].type)
        assertEquals(8, chunks[2].length)
    }

    private suspend fun createJdwpPacketWithThreeDdmsChunks(): MutableJdwpPacket {
        val chunk1 = createTestDdmsChunk(DdmsChunkTypes.REAQ, listOf(127, 128, 255, 0))
        val chunk2 = createTestDdmsChunk(DdmsChunkTypes.APNM, listOf())
        val chunk3 = createTestDdmsChunk(DdmsChunkTypes.HELO, listOf(1, 2, 3, 4, 5, 6, 7, 8))
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        chunk1.writeToChannel(output)
        chunk2.writeToChannel(output)
        chunk3.writeToChannel(output)
        val buffer = outputBuffer.forChannelWrite()

        val jdwpPacket = MutableJdwpPacket()
        jdwpPacket.packetLength = JdwpPacketConstants.PACKET_HEADER_LENGTH + buffer.remaining()
        jdwpPacket.packetId = 10
        jdwpPacket.packetCmdSet = DdmsPacketConstants.DDMS_CMD_SET
        jdwpPacket.packetCmd = DdmsPacketConstants.DDMS_CMD
        jdwpPacket.data = AdbBufferedInputChannel.forByteBuffer(buffer)
        return jdwpPacket
    }

    private fun createTestDdmsChunk(
        chunkType: Int = DdmsChunkTypes.REAQ,
        bytes: List<Int> = listOf(128, 0, 255, 10)
    ): DdmsChunkView {
        val ddmsChunk = MutableDdmsChunk()
        ddmsChunk.type = chunkType
        ddmsChunk.length = bytes.size
        ddmsChunk.data = bytesToBufferedInputChannel(bytes)
        return ddmsChunk
    }

    private fun bytesToBufferedInputChannel(bytes: List<Int>): AdbBufferedInputChannel {
        val buffer = ByteBuffer.allocate(bytes.size)
        for (value in bytes) {
            buffer.put(value.toByte())
        }
        buffer.flip()
        return AdbBufferedInputChannel.forByteBuffer(buffer)
    }
}
