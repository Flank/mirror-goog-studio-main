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

import com.android.adblib.AdbSession
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.DeviceSelector
import com.android.adblib.skipRemaining
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.clone
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.CoroutineTestUtils.waitNonNull
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SharedJdwpSessionTest : AdbLibToolsTestBase() {

    @Test
    fun nextPacketIdIsThreadSafe() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
        val threadCount = 100
        val packetCount = 1000
        val ids = (1..threadCount)
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
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
        val receivedPacketFlow = ReceivedPacketFlow(session.scope, jdwpSession)
        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)

        val reply = waitForReplyPacket(receivedPacketFlow, sendPacket)

        // Assert
        assertEquals(0, reply.errorCode)
        assertEquals(122, reply.length)
        assertEquals(111, reply.payload.countBytes())
    }

    @Test
    fun receivePacketsFlowEndsOnClientTerminate() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
        val receivedPacketFlow = ReceivedPacketFlow(session.scope, jdwpSession)
        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)
        fakeDevice.stopClient(10)

        // Assert
        assertTrue(receivedPacketFlow.packets.count() >= 0)
    }

    @Test
    fun receivePacketsFlowEndsConsistentlyOnClientTerminate() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
        val receivedPacketFlow = ReceivedPacketFlow(session.scope, jdwpSession)

        val sendPacket = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(sendPacket)
        waitForReplyPacket(receivedPacketFlow, sendPacket)
        fakeDevice.stopClient(10)

        // Assert
        assertTrue(receivedPacketFlow.packets.count() >= 0)
        assertTrue(receivedPacketFlow.packets.count() >= 0)
        assertTrue(receivedPacketFlow.packets.count() >= 0)
    }

    @Test
    fun sendPacketThrowExceptionAfterClose() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
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
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
        val receivedPacketFlow = ReceivedPacketFlow(session.scope, jdwpSession)
        val packet = createHeloDdmsPacket(jdwpSession)
        jdwpSession.sendPacket(packet)

        // Act
        jdwpSession.close()

        exceptionRule.expect(CancellationException()::class.java)
        waitForReplyPacket(receivedPacketFlow, packet)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun receivePacketFlowContainsReplayPackets() = runBlockingWithTimeout {
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createSession(fakeAdb)
        fakeDevice.startClient(10, 0, "a.b.c", false)

        // Act
        val jdwpSession = openSharedJdwpSession(session, deviceSelector, 10)
        val sendPacket1 = createHeloDdmsPacket(jdwpSession)
        val sendPacket2 = sendPacket1.clone().also { it.id = jdwpSession.nextPacketId() }
        val sendPacket3 = sendPacket1.clone().also { it.id = jdwpSession.nextPacketId() }
        jdwpSession.addReplayPacket(sendPacket2)
        jdwpSession.addReplayPacket(sendPacket3)
        jdwpSession.sendPacket(sendPacket1)

        val receivedPacketFlow = ReceivedPacketFlow(session.scope, jdwpSession)

        val cmd1 = waitForCommandPacket(receivedPacketFlow, sendPacket2)
        val cmd2 = waitForCommandPacket(receivedPacketFlow, sendPacket3)
        val reply1 = waitForReplyPacket(receivedPacketFlow, sendPacket1)

        // Assert
        assertEquals(0, reply1.errorCode)
        assertEquals(122, reply1.length)
        assertEquals(111, reply1.payload.countBytes())

        assertEquals(DdmsPacketConstants.DDMS_CMD_SET, cmd1.cmdSet)
        assertEquals(DdmsPacketConstants.DDMS_CMD_SET, cmd2.cmdSet)
    }


    private suspend fun waitForReplyPacket(
        receiveFlow: ReceivedPacketFlow,
        sendPacket: MutableJdwpPacket
    ): JdwpPacketView {
        return waitNonNull {
            receiveFlow.packets.firstOrNull {
                it.isReply && it.id == sendPacket.id
            }
        }
    }

    private suspend fun waitForCommandPacket(
        receiveFlow: ReceivedPacketFlow,
        sendPacket: MutableJdwpPacket
    ): JdwpPacketView {
        return waitNonNull {
            receiveFlow.packets.firstOrNull {
                it.isCommand && it.id == sendPacket.id
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

    private suspend fun createHeloDdmsPacket(jdwpSession: SharedJdwpSession): MutableJdwpPacket {
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

    private suspend fun openSharedJdwpSession(
        session: AdbSession,
        device: DeviceSelector,
        pid: Int
    ): SharedJdwpSession {
        val jdwpSession = JdwpSession.openJdwpSession(session, device, 10)
        return registerCloseable(SharedJdwpSession(session, pid, jdwpSession))
    }

    /**
     * Testing class to record all [JdwpPacketView] from a [SharedJdwpSession.receivePacketFlow]
     * flow, and expose them as a repeatable flow, see [packets].
     *
     * Note: Since this is a test utility class, we record only 100 [JdwpPacketView] instances,
     * which should be good enough for unit testing.
     */
    private class ReceivedPacketFlow(
        scope: CoroutineScope,
        private val session: SharedJdwpSession
    ) {
        companion object {
            const val MAXIMUM_RECORD_COUNT = 100
        }

        /**
         * Marker used to mark the end of the original flow in [packetsFlow]
         */
        private val flowCompletionMarker = Exception()

        private val flowReplayCacheSizeExceededMessage =
            "Maximum number of JDWP packets ($MAXIMUM_RECORD_COUNT) has been " +
                    " exceeded. This is probably due to a bug in the test."
        private fun Throwable.rethrowIfFlowReplayCacheSizeExceeded() {
            if (this.message == flowReplayCacheSizeExceededMessage) {
                throw this
            }
        }

        // We keep 100 packets which should be plenty enough for unit tests
        private val packetsFlow = MutableSharedFlow<Result<JdwpPacketView>>(MAXIMUM_RECORD_COUNT)

        val packets = flow {
            packetsFlow.asSharedFlow().takeWhile { result ->
                result.exceptionOrNull() !== flowCompletionMarker
            }.collect { result ->
                emit(result.getOrThrow())
            }
        }

        init {
            scope.launch {
                try {
                    session.receivePacketFlow.collect { packet ->
                        val clone = packet.clone()
                        if (packetsFlow.replayCache.size >= MAXIMUM_RECORD_COUNT) {
                            throw IllegalStateException(flowReplayCacheSizeExceededMessage)
                        }

                        packetsFlow.emit(Result.success(clone))
                    }
                    packetsFlow.emit(Result.failure(flowCompletionMarker))
                } catch(t: Throwable) {
                    // This exception should go directly to the flow collector
                    t.rethrowIfFlowReplayCacheSizeExceeded()
                    packetsFlow.emit(Result.failure(t))
                }
            }
        }
    }
}
