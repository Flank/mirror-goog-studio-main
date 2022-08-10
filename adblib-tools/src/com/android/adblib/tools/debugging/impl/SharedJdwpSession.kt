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
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.clone
import com.android.adblib.tools.debugging.utils.SharedSerializedFlow
import com.android.adblib.tools.debugging.utils.SharedSerializedFlowWithReplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.EOFException

/**
 * A thread-safe version of [JdwpSession].
 *
 * Instead of exposing a [JdwpSession.receivePacket] method that reads directly from the JDWP
 * session channel, this class exposes a [receivePacketFlow] property that returns a
 * [Flow] of [JdwpPacketView]. The returned [Flow] instance can be collected concurrently by an
 * arbitrary number of coroutines.
 */
internal class SharedJdwpSession(
    session: AdbSession,
    private val pid: Int,
    private val jdwpSession: JdwpSession
) : AutoCloseable {

    private val logger = thisLogger(session)

    /**
     * A [SharedSerializedFlow] that reads JDWP packets sequentially from the underlying
     * [JdwpSession], making receiving packets thread-safe (see [receivePacketFlow])
     */
    private val sharedSerializedFlow = SharedSerializedFlowWithReplay(
        session = session,
        upstreamFlow = jdwpSession.toUpstreamFlow(),
        replayKeyProvider = { packet -> packet.id }
    )

    /**
     * The thread-safe [Flow] of [JdwpPacketView] received from this JDWP session.
     *
     * Note that the [JdwpPacketView] instances are only guaranteed to be valid at the time
     * of collection, because the underlying implementation re-uses instances between calls.
     *
     * Note also that [Flow] collectors should process each packet quickly, as each collector
     * is called sequentially since [JdwpPacketView] instances are not thread-safe.
     *
     * If a [JdwpPacketView] instance needs to be kept around across calls, collectors should
     * use the [JdwpPacketView.clone] function to make an in-memory copy of the packet.
     *
     * See [SharedSerializedFlow] for additional implementation details.
     */
    val receivePacketFlow: Flow<JdwpPacketView> = flow {
        sharedSerializedFlow.flow.collect { packet ->
            logger.verbose { "pid=$pid: Emitting JDWP packet '$packet' from shared JDWP session" }
            // We need to rewind the payload stream for each consumer
            packet.payload.rewind()
            emit(packet)
        }
    }

    /**
     * Sends a [JdwpPacketView] to this session, after ensuring the JDWP handshake is performed.
     */
    suspend fun sendPacket(packet: JdwpPacketView) {
        jdwpSession.sendPacket(packet)
    }

    fun nextPacketId(): Int {
        return jdwpSession.nextPacketId()
    }

    suspend fun addReplayPacket(packet: JdwpPacketView) {
        logger.verbose { "pid=$pid: Adding JDWP replay packet '$packet'" }
        val clone = packet.clone()
        sharedSerializedFlow.addReplayValue(packet)
    }

    override fun close() {
        logger.debug { "pid=$pid: Closing" }
        jdwpSession.close()
        sharedSerializedFlow.close()
    }

    private fun JdwpSession.toUpstreamFlow() = flow {
        while (true) {
            logger.verbose { "pid=$pid: Waiting for next JDWP packet from session" }
            val packet = try {
                this@toUpstreamFlow.receivePacket()
            } catch (e: EOFException) {
                // Reached EOF, flow terminates
                logger.debug { "pid=$pid: JDWP session has ended with EOF" }
                break
            }
            logger.verbose { "pid=$pid: Emitting session packet to upstream flow: $packet" }
            emit(packet)
        }
    }
}
