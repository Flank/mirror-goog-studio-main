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

import com.android.adblib.ByteBufferAdbInputChannel
import com.android.adblib.readRemaining
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.utils.ResizableBuffer
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer

class AdbBufferedInputChannelTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testEmptyInputChannel() = runBlockingWithTimeout {
        // Act
        val channel = AdbBufferedInputChannel.empty()
        val outputBuffer = ResizableBuffer()
        val byteCount = channel.readRemaining(outputBuffer)
        channel.rewind()

        // Assert
        assertEquals(0, byteCount)
        assertEquals(0, outputBuffer.position)
    }

    @Test
    fun testForInputChannelSupportsReadingAllBytes() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val bufferedPacketChannel = AdbBufferedInputChannel.forInputChannel(adbInputChannel)
        val workBuffer = ResizableBuffer()
        val byteCount = bufferedPacketChannel.readRemaining(workBuffer, 20)

        // Assert
        assertEquals(1_024, byteCount)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer.afterChannelRead(0).toByteArray().toList()
        )
    }

    @Test
    fun testForInputChannelSupportsRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val bufferedPacketChannel = AdbBufferedInputChannel.forInputChannel(adbInputChannel)
        val workBuffer1 = ResizableBuffer()
        val byteCount1 = bufferedPacketChannel.readRemaining(workBuffer1, 20)

        bufferedPacketChannel.rewind()
        val workBuffer2 = ResizableBuffer()
        val byteCount2 = bufferedPacketChannel.readRemaining(workBuffer2, 20)

        // Assert
        assertEquals(1_024, byteCount1)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer1.afterChannelRead(0).toByteArray().toList()
        )

        assertEquals(1_024, byteCount2)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer2.afterChannelRead(0).toByteArray().toList()
        )
    }

    @Test
    fun testForInputChannelSupportsFinalRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val bufferedPacketChannel = AdbBufferedInputChannel.forInputChannel(adbInputChannel)
        val workBuffer1 = ResizableBuffer()
        val byteCount1 = bufferedPacketChannel.readRemaining(workBuffer1, 20)

        bufferedPacketChannel.finalRewind()
        val workBuffer2 = ResizableBuffer()
        val byteCount2 = bufferedPacketChannel.readRemaining(workBuffer2, 20)

        // Assert
        assertEquals(1_024, byteCount1)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer1.afterChannelRead(0).toByteArray().toList()
        )

        assertEquals(1_024, byteCount2)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer2.afterChannelRead(0).toByteArray().toList()
        )
    }

    /**
     * The purpose of this test is to hit a code path in the implementation of
     * [AdbBufferedInputChannel.forInputChannel] where all buffering is skipped
     * when [AdbBufferedInputChannel.finalRewind] is called before any
     * [AdbBufferedInputChannel.read] operation.
     */
    @Test
    fun testForInputChannelSupportsFinalRewindBeforeReadingAnything() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val bufferedPacketChannel = AdbBufferedInputChannel.forInputChannel(adbInputChannel)
        bufferedPacketChannel.finalRewind()
        val workBuffer = ResizableBuffer()
        val byteCount = bufferedPacketChannel.readRemaining(workBuffer, 20)

        // Assert
        assertEquals(1_024, byteCount)
        assertEquals(
            (0 until 1_024).map { (it and 0xff).toByte() },
            workBuffer.afterChannelRead(0).toByteArray().toList()
        )
    }

    @Test
    fun testForInputChannelAllowsOnlyOneFinalRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)
        val bufferedPacketChannel = AdbBufferedInputChannel.forInputChannel(adbInputChannel)

        // Act
        bufferedPacketChannel.readRemaining(ResizableBuffer(), 20)
        bufferedPacketChannel.finalRewind()
        bufferedPacketChannel.readRemaining(ResizableBuffer(), 20)

        exceptionRule.expect(IllegalStateException::class.java)
        bufferedPacketChannel.finalRewind()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testForInputChannelDoesNotAllowRewindAfterLastRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(2_048)
        for(i in 0 until 1_024) {
            buffer.put((i and 0xff).toByte())
        }
        buffer.flip()
        val adbInputChannel = ByteBufferAdbInputChannel(buffer)
        val bufferedPacketChannel = AdbBufferedInputChannel.forInputChannel(adbInputChannel)

        // Act
        bufferedPacketChannel.readRemaining(ResizableBuffer(), 20)
        bufferedPacketChannel.finalRewind()
        bufferedPacketChannel.readRemaining(ResizableBuffer(), 20)

        exceptionRule.expect(IllegalStateException::class.java)
        bufferedPacketChannel.rewind()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testForByteBufferReadsAllData() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()

        // Act
        val channel = AdbBufferedInputChannel.forByteBuffer(buffer)
        val outputBuffer = ResizableBuffer()
        val byteCount = channel.readRemaining(outputBuffer)

        // Assert
        assertEquals(2, byteCount)
    }

    @Test
    fun testForByteBufferSupportsRewind() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(10)
        buffer.put(5)
        buffer.put(6)
        buffer.flip()
        val channel = AdbBufferedInputChannel.forByteBuffer(buffer)
        channel.readRemaining(ResizableBuffer())
        channel.rewind()
        val workBuffer = ResizableBuffer()

        // Act
        workBuffer.clear()
        val byteCount = channel.readRemaining(workBuffer)
        channel.rewind()

        workBuffer.clear()
        val byteCount2 = channel.readRemaining(workBuffer)

        // Assert
        assertEquals(2, byteCount)
        assertEquals(2, byteCount2)
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val result = ByteArray(this.remaining())
        this.get(result)
        this.position(this.position() - result.size)
        return result
    }
}
