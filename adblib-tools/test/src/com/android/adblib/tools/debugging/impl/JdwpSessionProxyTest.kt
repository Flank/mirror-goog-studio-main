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
import com.android.adblib.createDeviceScope
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.tools.debugging.JdwpSessionHandler
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsHeloChunk
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.tools.testutils.CoroutineTestUtils.waitNonNull
import com.android.adblib.tools.testutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.utils.ResizableBuffer
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JdwpSessionProxyTest : AdbLibToolsTestBase() {

    @Test
    fun socketAddressIsAssignedAutomatically() = runBlockingWithTimeout {
        val deviceID = "1234"
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createHostServices(fakeAdb).session
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val deviceScope = session.createDeviceScope(deviceSelector)

        // Act
        val process = registerCloseable(JdwpProcessImpl(session, deviceSelector, deviceScope, 10))
        process.startMonitoring()
        yieldUntil {
            process.properties.jdwpSessionProxyStatus.socketAddress != null
        }

        // Assert
        assertFalse(process.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
    }

    @Test
    fun socketAddressSupportsJdwpSession() = runBlockingWithTimeout {
        val deviceID = "1234"
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice =
            fakeAdb.connectDevice(
                deviceID,
                "test1",
                "test2",
                "model",
                "30", // SDK >= 30 is required for abb_exec feature.
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val session = createHostServices(fakeAdb).session
        fakeDevice.startClient(10, 0, "a.b.c", false)
        val deviceScope = session.createDeviceScope(deviceSelector)

        // Act
        val process = registerCloseable(JdwpProcessImpl(session, deviceSelector, deviceScope, 10))
        process.startMonitoring()
        yieldUntil {
            process.properties.jdwpSessionProxyStatus.socketAddress != null
        }
        val clientSocket = registerCloseable(session.channelFactory.connectSocket(process.properties.jdwpSessionProxyStatus.socketAddress!!))
        val jdwpSession = registerCloseable(JdwpSessionHandler.wrapSocketChannel(session, clientSocket, 10))

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

        jdwpSession.sendPacket(packet)

        val reply = waitNonNull {
            val r = jdwpSession.receivePacket()
            if (r.id == packet.id) r else null
        }
        val heloReply = reply.ddmsChunks().first()
        val heloReplyChunk = DdmsHeloChunk.parse(heloReply)

        // Assert
        assertTrue(reply.isReply)
        assertEquals(packet.id, reply.id)
        assertEquals(10, heloReplyChunk.pid)
    }

    private suspend fun DdmsChunkView.toBufferedInputChannel(): AdbBufferedInputChannel {
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        this.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()
        return AdbBufferedInputChannel.forByteBuffer(serializedChunk)
    }
}
