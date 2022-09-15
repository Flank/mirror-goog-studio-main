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
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.tools.debugging.impl.JdwpSessionImpl
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.utils.closeOnException
import java.io.EOFException
import java.io.IOException

/**
 * Abstraction over a JDWP session with a device.
 *
 * Note: The session [close] method must be called to terminate the JDWP session.
 * Closing a session only closes the underlying communication channel, without having
 * any effect on the process VM, i.e. the process VM is not terminated.
 *
 * @see [AdbDeviceServices.jdwp]
 */
internal interface JdwpSession : AutoCloseable {

    /**
     * Sends a [JdwpPacketView] to the process VM.
     *
     * @throws [IOException] if an I/O error occurs
     * @throws [Exception] if any other error occurs
     */
    suspend fun sendPacket(packet: JdwpPacketView)

    /**
     * Waits for (and returns) the next [JdwpPacketView] from the process VM.
     *
     * @throws [EOFException] if there are no more packets from the process VM,
     *   i.e. the JDWP session has terminated.
     * @throws [IOException] if an I/O error occurs
     * @throws [Exception] if any other error occurs
     */
    suspend fun receivePacket(): JdwpPacketView

    /**
     * Returns a unique [JDWP packet ID][JdwpPacketView.id] to use for sending
     * a [JdwpPacketView], typically a [command packet][JdwpPacketView.isCommand],
     * in this session. Each call returns a new unique value.
     *
     * Note: This method is thread-safe.
     */
    fun nextPacketId(): Int

    companion object {

        /**
         * Returns a [JdwpSession] that opens a `JDWP` session for the given process [pid]
         * on the given [device].
         *
         * @see [AdbDeviceServices.jdwp]
         */
        suspend fun openJdwpSession(
            session: AdbSession,
            device: DeviceSelector,
            pid: Int
        ): JdwpSession {
            val channel = session.deviceServices.jdwp(device, pid)
            channel.closeOnException {
                return JdwpSessionImpl(session, channel, pid)
            }
        }

        /**
         * Returns a [JdwpSession] that wraps an existing socket [channel] and allows
         * exchanging `JDWP` packets.
         */
        fun wrapSocketChannel(session: AdbSession, channel: AdbChannel, pid: Int, firstPacketId: Int = 100): JdwpSession {
            return JdwpSessionImpl(session, channel, pid, firstPacketId)
        }
    }
}
