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

import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.utils.ResizableBuffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class JdwpPacketViewTest : AdbLibToolsTestBase() {

    @Test
    fun testJdwpPacketViewWrite() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestCmdPacket(20)

        // Act
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packet.writeToChannel(output)

        // Assert: Data should be from [0, outputBuffer.position[
        var index = 0
        assertEquals(31, outputBuffer.position)

        // Length
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(31.toByte(), outputBuffer[index++])

        // Id
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(77.toByte(), outputBuffer[index++])

        // Flag + CmdSet + Cmd
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(15.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index++])

        // Contents
        assertTestBufferContents(outputBuffer, index)
    }

    @Test
    fun testJdwpPacketViewWriteLargePacket() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestCmdPacket(10_000)

        // Act
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packet.writeToChannel(output)

        // Assert: Data should be from [0, outputBuffer.position[
        var index = 0
        assertEquals(10_011, outputBuffer.position)

        // Length
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(39.toByte(), outputBuffer[index++])
        assertEquals(27.toByte(), outputBuffer[index++])

        // Id
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(77.toByte(), outputBuffer[index++])

        // Flag, CmdSet, Cmd
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(15.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index++])

        // Contents
        assertTestBufferContents(outputBuffer, index)
    }

    @Test
    fun testJdwpPacketViewCloneContainsSameData() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestCmdPacket(20)

        // Act
        val outputBuffer = ResizableBuffer()
        val packetClone = packet.clone(outputBuffer)

        // Assert
        assertCloneIsCorrect(packetClone)
    }

    @Test
    fun testJdwpPacketViewCloneCanCloneMultipleTimes() = runBlockingWithTimeout {
        // Prepare
        val packet = createTestCmdPacket(20)

        // Act
        val buffer1 = ResizableBuffer()
        val clone1 = packet.clone(buffer1)
        val buffer2 = ResizableBuffer()
        val clone2 = packet.clone(buffer2)

        // Assert
        assertCloneIsCorrect(clone1)
        assertCloneIsCorrect(clone2)
    }

    private suspend fun assertCloneIsCorrect(packetClone: JdwpPacketView) {
        val outputBuffer = ResizableBuffer()
        val output = ByteBufferAdbOutputChannel(outputBuffer)
        packetClone.writeToChannel(output)

        // Data should be from [0, outputBuffer.position[
        var index = 0
        assertEquals(31, outputBuffer.position)

        // Length
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(31.toByte(), outputBuffer[index++])

        // Id
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(77.toByte(), outputBuffer[index++])

        // Flag + CmdSet + Cmd
        assertEquals(0.toByte(), outputBuffer[index++])
        assertEquals(15.toByte(), outputBuffer[index++])
        assertEquals(10.toByte(), outputBuffer[index++])

        // Contents
        assertTestBufferContents(outputBuffer, index)
    }

    private fun assertTestBufferContents(buffer: ResizableBuffer, offset: Int) {
        for (i in 0 until buffer.position - offset) {
            assertEquals((i % 127).toByte(), buffer[offset + i])
        }
    }

    private fun createTestCmdPacket(size: Int): JdwpPacketView {
        val packet = MutableJdwpPacket()
        packet.length = size + JdwpPacketConstants.PACKET_HEADER_LENGTH
        packet.id = 77
        packet.cmdSet = 15
        packet.cmd = 10

        val buffer = ByteBuffer.allocate(size)
        for (i in 0 until size) {
            buffer.put((i % 127).toByte())
        }
        buffer.flip()
        assert(buffer.position() == 0)
        assert(buffer.limit() == size)
        assert(buffer.capacity() == size)
        packet.payload = AdbBufferedInputChannel.forByteBuffer(buffer)
        return packet
    }
}
