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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbInputChannelSlice
import com.android.adblib.AdbSession
import com.android.adblib.AdbOutputChannel
import com.android.adblib.skipRemaining
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpSessionHandler
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_BYTE_ORDER
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.parseHeader
import com.android.adblib.tools.debugging.packets.writeToChannel
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

internal class JdwpSessionHandlerImpl(
  session: AdbSession,
  private val channel: AdbChannel,
  private val pid: Int
) : JdwpSessionHandler {

    private val logger = thisLogger(session)

    private val inputChannel: AdbInputChannel
        get() = channel

    private val outputChannel: AdbOutputChannel
        get() = channel

    private val handshakeHandler = HandshakeHandler(session, pid, inputChannel, outputChannel)

    private val sender = Sender(outputChannel)

    private val receiver = Receiver(inputChannel)

    private val sendMutex = Mutex()

    private val receiveMutex = Mutex()

    private val atomicPacketId = AtomicInteger(100)

    override fun close() {
        logger.debug { "pid=$pid: Closing JDWP session handler (and underlying channel)" }
        inputChannel.close()
        outputChannel.close()
        channel.close()
    }

    override suspend fun sendPacket(packet: JdwpPacketView) {
        // We serialize sending packets to the channel so that we always send fully formed packets
        sendMutex.withLock {
            sendHandshake()
            logger.debug { "pid=$pid: Sending JDWP packet: $packet" }
            sender.sendPacket(packet)
        }
    }

    override suspend fun receivePacket(): JdwpPacketView {
        // We serialize reading packets from the channel so that we always read fully formed packets
        receiveMutex.withLock {
            waitForHandshake()
            val packet = receiver.receivePacket()
            logger.debug { "pid=$pid: Receiving JDWP packet: $packet" }
            return packet
        }
    }

    override fun nextPacketId(): Int = atomicPacketId.getAndIncrement()

    private suspend fun sendHandshake() {
        handshakeHandler.sendHandshake()
    }

    private suspend fun waitForHandshake() {
        handshakeHandler.waitForHandshake()
    }

    class HandshakeHandler(
      session: AdbSession,
      private val pid: Int,
      private val inputChannel: AdbInputChannel,
      private val outputChannel: AdbOutputChannel
    ) {

        private val logger = thisLogger(session)
        private val channelMutex = Mutex()
        private var handshakeSent: Boolean = false
        private var handshakeReceived: Boolean = false

        suspend fun sendHandshake() {
            // Short-circuit: We don't need synchronization here, as the field is only set
            // once the handshake has already been sent
            if (handshakeSent) {
                return
            }

            channelMutex.withLock {
                // Execution is serialized here
                val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)
                sendHandshakeWorker(workBuffer)
            }
        }

        suspend fun waitForHandshake() {
            // Short-circuit: We don't need synchronization here, as the field is only set
            // once the handshake has been fully received
            if (handshakeReceived) {
                return
            }

            channelMutex.withLock {
                // Execution is serialized here
                if (!handshakeReceived) {
                    val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)
                    sendHandshakeWorker(workBuffer)

                    logger.debug { "pid=$pid: Waiting for JDWP handshake from Android VM" }
                    receiveJdwpHandshake(workBuffer)
                    logger.debug { "pid=$pid: JDWP handshake received from Android VM" }

                    handshakeReceived = true
                }
            }
        }

        private suspend fun sendHandshakeWorker(workBuffer: ResizableBuffer) {
            if (!handshakeSent) {
                logger.debug { "pid=$pid: Sending JDWP handshake to Android VM" }
                workBuffer.clear()
                workBuffer.appendBytes(HANDSHAKE)
                val data = workBuffer.forChannelWrite()
                outputChannel.writeExactly(data)
                handshakeSent = true
            }
        }

        private suspend fun receiveJdwpHandshake(workBuffer: ResizableBuffer) {
            //TODO: This could be more efficient
            val bytesSoFar = ArrayList<Byte>()
            while (true) {
                bytesSoFar.add(readOneByte(workBuffer))

                if (isJdwpHandshake(bytesSoFar)) {
                    return
                }
                processEarlyJdwpPacket(bytesSoFar)
            }
        }

        private fun processEarlyJdwpPacket(bytesSoFar: MutableList<Byte>) {
            // See bug 178655046: There was a race condition in JDWP connection handling
            // for many years that resulted in APNM packets sometimes being sent before
            // the JDWP handshake.
            // This was eventually fixed in https://android-review.googlesource.com/c/platform/art/+/1569323
            // by making sure such packets are not sent until the handshake is sent.
            // Given the "APNM" packet is redundant with the "HELO" packet, we simply ignore
            // such pre-handshake packets.
            if (bytesSoFar.size >= PACKET_HEADER_LENGTH) {
                val buffer = ByteBuffer.wrap(bytesSoFar.toByteArray()).order(PACKET_BYTE_ORDER)
                val packet = MutableJdwpPacket()
                packet.parseHeader(buffer)
                if (packet.length - PACKET_HEADER_LENGTH <= buffer.remaining()) {
                    packet.payload = AdbBufferedInputChannel.forByteBuffer(buffer)
                    logger.debug { "pid=$pid:  Skipping JDWP packet received before JDWP handshake: $packet" }
                    bytesSoFar.clear()
                }
            }
        }

        private fun isJdwpHandshake(bytesSoFar: List<Byte>): Boolean {
            //TODO: This could be more efficient
            val bytesSoFarIndex = bytesSoFar.size - HANDSHAKE.size
            if (bytesSoFarIndex < 0) {
                return false
            }

            for (i in HANDSHAKE.indices) {
                if (bytesSoFar[bytesSoFarIndex + i] != HANDSHAKE[i]) {
                    return false
                }
            }
            return true
        }

        private suspend fun readOneByte(workBuffer: ResizableBuffer): Byte {
            workBuffer.clear()
            inputChannel.readExactly(workBuffer.forChannelRead(1))
            return workBuffer.afterChannelRead().get()
        }
    }

    class Sender(private val channel: AdbOutputChannel) {

        private val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)

        suspend fun sendPacket(packet: JdwpPacketView) {
            packet.writeToChannel(channel, workBuffer)
        }
    }

    class Receiver(private val channel: AdbInputChannel) {

        private val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)

        private val jdwpPacket = MutableJdwpPacket()

        suspend fun receivePacket(): JdwpPacketView {
            // Ensure we consume all bytes from the previous packet
            jdwpPacket.payload.finalRewind()
            jdwpPacket.payload.skipRemaining(workBuffer)

            // Read next packet
            readOnePacket(workBuffer, jdwpPacket)
            return jdwpPacket
        }

        private suspend fun readOnePacket(workBuffer: ResizableBuffer, packet: MutableJdwpPacket) {
            workBuffer.clear()
            channel.readExactly(workBuffer.forChannelRead(PACKET_HEADER_LENGTH))
            packet.parseHeader(workBuffer.afterChannelRead())
            packet.payload =
                AdbBufferedInputChannel.forInputChannel(
                    AdbInputChannelSlice(
                        channel,
                        packet.length - PACKET_HEADER_LENGTH
                    )
                )
        }
    }

    companion object {

        private val HANDSHAKE = "JDWP-Handshake".toByteArray(Charsets.US_ASCII)
    }
}
