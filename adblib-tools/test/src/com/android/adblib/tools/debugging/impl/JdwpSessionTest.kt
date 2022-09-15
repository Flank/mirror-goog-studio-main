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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.DeviceSelector
import com.android.adblib.skipRemaining
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.waitNonNull
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.EOFException
import java.io.IOException

class JdwpSessionTest : AdbLibToolsTestBase() {

    @Test
    fun nextPacketIdIsThreadSafe() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))
        val threadCount = 100
        val packetCount = 1000
        val ids =(1..threadCount)
            .map {
                async {
                    (1..packetCount).map {
                        jdwpSession.nextPacketId()
                    }.toSet()
                }
            }
            .awaitAll()
            .flatten()
            .toSet()

        // Assert
        assertEquals(threadCount * packetCount, ids.size)
    }

    @Test
    fun sendAndReceivePacketWorks() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))

        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)

        val reply = waitForReplyPacket(jdwpSession, sendPacket)

        // Assert
        assertEquals(0, reply.errorCode)
        assertEquals(122, reply.length)
        assertEquals(111, reply.payload.countBytes())
    }

    @Test
    fun receivePacketRewindsTheCorrectPayload() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))

        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)

        val reply = waitForReplyPacket(jdwpSession, sendPacket)

        sendPacket.id = jdwpSession.nextPacketId()
        jdwpSession.sendPacket(sendPacket)

        // Override payload (in an unfriendly way)
        (reply as MutableJdwpPacket).payload = AdbBufferedInputChannel.empty()
        val reply2 = waitForReplyPacket(jdwpSession, sendPacket)

        // Assert
        assertEquals(0, reply.errorCode)
        assertEquals(0, reply2.errorCode)
    }

    @Test
    fun receivePacketThrowsEofOnClientTerminate() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))

        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)
        fakeDevice.stopClient(10)

        kotlin.runCatching { waitForReplyPacket(jdwpSession, sendPacket) }

        // Assert
        assertThrows<EOFException> { jdwpSession.receivePacket() }
    }

    @Test
    fun receivePacketThrowsEofConsistentlyOnClientTerminate() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))

        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)
        waitForReplyPacket(jdwpSession, sendPacket)
        fakeDevice.stopClient(10)

        // Assert
        assertThrows<EOFException> { jdwpSession.receivePacket() }
        assertThrows<EOFException> { jdwpSession.receivePacket() }
        assertThrows<EOFException> { jdwpSession.receivePacket() }
    }

    @Test
    fun sendPacketThrowExceptionAfterClose() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))
        val packet = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(packet)

        // Act
        jdwpSession.close()
        exceptionRule.expect(IOException::class.java)
        jdwpSession.sendPacket(packet)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun receivePacketThrowExceptionAfterClose() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = registerCloseable(JdwpSession.openJdwpSession(session, deviceSelector, 10))
        val packet = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(packet)

        // Act
        jdwpSession.close()

        exceptionRule.expect(IOException::class.java)
        waitForReplyPacket(jdwpSession, packet)

        // Assert
        fail("Should not reach")
    }

    private suspend fun waitForReplyPacket(
        jdwpSession: JdwpSession,
        sendPacket: MutableJdwpPacket
    ): JdwpPacketView {
        return waitNonNull {
            val r = jdwpSession.receivePacket()
            if (r.isReply && r.id == sendPacket.id) {
                r
            } else {
                null
            }
        }
    }

    private suspend fun DdmsChunkView.toBufferedInputChannel(): AdbBufferedInputChannel {
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        this.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()
        return AdbBufferedInputChannel.forByteBuffer(serializedChunk)
    }

    private suspend fun createHeloDdmsPacket(jdwpSession: JdwpSession): MutableJdwpPacket {
        val heloChunk = MutableDdmsChunk()
        heloChunk.type = DdmsChunkTypes.HELO
        heloChunk.length = 0
        heloChunk.payload = AdbBufferedInputChannel.empty()

        val packet = MutableJdwpPacket()
        packet.id = jdwpSession.nextPacketId()
        packet.length = 11 + 8
        packet.isCommand = true
        packet.cmdSet = DdmsPacketConstants.DDMS_CMD_SET
        packet.cmd = DdmsPacketConstants.DDMS_CMD
        packet.payload = heloChunk.toBufferedInputChannel()
        return packet
    }

    private suspend fun AdbBufferedInputChannel.countBytes(): Int {
        return skipRemaining().also {
            rewind()
        }
    }
}
