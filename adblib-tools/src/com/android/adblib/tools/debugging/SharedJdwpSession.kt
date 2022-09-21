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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSession
import com.android.adblib.tools.debugging.impl.SharedJdwpSessionImpl
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import java.io.EOFException

/**
 * A thread-safe version of [JdwpSession] that consumes packets on-demand via [newPacketReceiver]
 *
 * * For sending packets, the [sendPacket] methods works the same way as the underlying
 *   [JdwpSession.sendPacket], i.e. it is thread-safe and automatically handle the JDWP
 *   handshake. One difference is that writing to the underlying [socket][AdbChannel]
 *   is performed in a custom [CoroutineScope] so that cancellation of the caller
 *   (e.g. timeout) does not close the underlying socket. Closing the underlying socket
 *   would mean all other consumers of this JDWP session would be unable to perform
 *   any other operation on it.
 *
 * * For receiving packets, the [newPacketReceiver] method allows callers to register a
 *   [JdwpPacketReceiver] that exposes a [Flow] of [JdwpPacketView] for collecting *all*
 *   packets received from the JDWP session. Receivers are called sequentially and should
 *   handle their exceptions. Similar to [sendPacket] behavior, any I/O performed on the
 *   underlying [AdbChannel] is executed in a custom [CoroutineScope] so that cancellation
 *   of receivers coroutine does not close the underlying [socket][AdbChannel].
 */
internal interface SharedJdwpSession : AutoCloseable {

    /**
     * The process ID this [SharedJdwpSession] handles
     */
    val pid: Int

    /**
     * Sends a [JdwpPacketView] to the underlying [JdwpSession].
     *
     * Note this method can block until the underlying communication channel
     * "send" buffer has enough room to store the packet.
     */
    suspend fun sendPacket(packet: JdwpPacketView)

    /**
     * Creates a [JdwpPacketReceiver] to collect [JdwpPacketView] coming from the
     * underlying [JdwpSession].
     *
     * ### Usage
     *
     *       session.newReceiver()
     *          .withName("Foo") // An arbitrary name used for debugging
     *          .onActivation {
     *              // Receiver has been activated and is guaranteed to receive all
     *              // received packets from this point on
     *          }
     *          .collect { packet ->
     *              // Receiver is active and a packet has been received.
     *              // The receiver has exclusive access to the packet until this block
     *              // ends.
     *          }
     *
     * ### Notes
     *
     * A [JdwpPacketReceiver] is initially **inactive**, i.e. does not collect packets and
     * does not make this [SharedJdwpSession] start consuming packets from the underlying
     * [JdwpSession]. A [JdwpPacketReceiver] is **activated** by calling [JdwpPacketReceiver.flow]
     * (or the [JdwpPacketReceiver.collect] shortcut).
     *
     * * All active [JdwpPacketReceiver]s are guaranteed to be invoked sequentially, meaning
     *   they can freely use any field of the [JdwpPacketView], as well as consume the
     *   [JdwpPacketView.payload] without any explicit synchronization. This also
     *   implies that **receivers should process packets quickly** to prevent blocking
     *   other receivers. Conceptually, the [SharedJdwpSession] works like this
     *
     *
     *    while(!EOF) {
     *      val packet = collect one JdwpPacketView from the JdwpSession
     *      activeReceiverFlowCollectors.forEach {
     *        it.emit(packet)
     *      }
     *    }
     *
     * * Active [JdwpPacketReceiver]s are cancelled if this session is [closed][close].
     *
     * * Active [JdwpPacketReceiver]s are notified of the termination of the underlying [JdwpSession]
     *   with a [Throwable]. A "normal" termination is an [EOFException].
     *
     *   @see JdwpPacketReceiver
     */
    suspend fun newPacketReceiver(): JdwpPacketReceiver

    /**
     * Returns a unique [JDWP packet ID][JdwpPacketView.id] to use for sending
     * a [JdwpPacketView], typically a [command packet][JdwpPacketView.isCommand],
     * in this session. Each call returns a new unique value.
     *
     * See [JdwpSession.nextPacketId]
     */
    fun nextPacketId(): Int

    /**
     * Add a [JdwpPacketView] to the list of "replay packets", i.e. the list of [JdwpPacketView]
     * that each new [receiver][newPacketReceiver] receives before any other packet from the
     * underlying [JdwpSession].
     */
    suspend fun addReplayPacket(packet: JdwpPacketView)

    companion object {

        fun create(session: AdbSession, pid: Int, jdwpSession: JdwpSession): SharedJdwpSession {
            return SharedJdwpSessionImpl(session, pid, jdwpSession)
        }
    }
}

/**
 * Provides access to a [Flow] of [JdwpPacketView]
 *
 * @see SharedJdwpSession.newPacketReceiver
 */
abstract class JdwpPacketReceiver {
    var name: String = ""
        private set

    protected var activation: suspend () -> Unit = { }
        private set

    /**
     * Sets an arbitrary name for this receiver
     */
    fun withName(name: String): JdwpPacketReceiver {
        this.name = name
        return this
    }

    /**
     * Sets a [block] that is invoked when this receiver is activated, but before
     * any [JdwpPacketView] is received.
     */
    fun onActivation(block: suspend () -> Unit): JdwpPacketReceiver {
        activation = block
        return this
    }

    /**
     * Returns a [Flow] of [JdwpPacketView] for this [JdwpPacketReceiver].
     *
     * When the flow is collected,
     * 1. the (optional) lambda passed to [onActivation] is invoked,
     * 1. all replay packets are sent to the collector
     * 1. the underlying [SharedJdwpSession] is activated if needed, i.e. [JdwpPacketView]
     * are read from the underlying [JdwpSession]
     * 1. [JdwpPacketView] received from the underlying [JdwpSession] are emitted to the
     * flow collector,
     *
     * ### Notes
     *
     * * The returned [Flow] ends when the underlying [SharedJdwpSession] reaches EOF.
     * * The returned [Flow] throws when the underlying [SharedJdwpSession] ends for
     *   any other reason than EOF.
     * * This flow implementation guarantees all collectors invoked sequentially,
     *   allowing them to consume [JdwpPacketView.payload] without explicit synchronization.
     */
    abstract fun flow(): Flow<JdwpPacketView>

    /**
     * Shortcut for
     *
     *    flow().collect { action(it) }
     *
     * @see flow
     */
    suspend inline fun collect(crossinline action: suspend (value: JdwpPacketView) -> Unit) {
        flow().collect { action(it) }
    }
}
