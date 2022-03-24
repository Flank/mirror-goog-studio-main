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
package com.android.fakeadbserver

import com.google.common.base.Charsets
import java.io.EOFException
import java.net.Socket

/**
 * Abstraction over access to stdin/stdout/stderr/exit code of an ADB shell command.
 *
 * This allows shell command implementor in [FakeAdbServer] to have a single implementation
 * that can deal with the simplified stdin/stdout protocol, or the full [ShellV2Protocol].
 */
interface ServiceOutput {

    fun writeStdout(text: String)
    fun writeStderr(text: String)
    fun writeExitCode(exitCode: Int)

    fun readStdin(bytes: ByteArray, offset: Int, length: Int): Int
}

/**
 * Implementation of [ServiceOutput] that write stdout/stderr directly to
 * [Socket.getOutputStream], and ignores exit code.
 */
class ExecServiceOutput(socket: Socket) : ServiceOutput {

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    private fun writeString(string: String) {
        output.write(string.toByteArray(Charsets.UTF_8))
    }

    override fun writeStdout(text: String) {
        writeString(text)
    }

    override fun writeStderr(text: String) {
        writeString(text)
    }

    override fun writeExitCode(exitCode: Int) {
        // This is not implemented for this version of the protocol
    }

    override fun readStdin(bytes: ByteArray, offset: Int, length: Int): Int {
        return input.read(bytes, offset, length)
    }
}

/**
 * Implementation of [ServiceOutput] that read and writes from/to the underlying socket
 * using the [ShellV2Protocol].
 */
class ShellProtocolServiceOutput(socket: Socket) : ServiceOutput {

    private val protocol = ShellV2Protocol(socket)

    private var currentStdinPacket: ShellV2Protocol.Packet? = null
    private var currentStdinPacketOffset = 0

    override fun writeStdout(text: String) {
        protocol.writeStdout(text)
    }

    override fun writeStderr(text: String) {
        protocol.writeStderr(text)
    }

    override fun writeExitCode(exitCode: Int) {
        protocol.writeExitCode(exitCode)
    }

    override fun readStdin(bytes: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            // Process current packet if there is one (and it has remaining data)
            val packet = currentStdinPacket
            if (packet != null) {
                if (currentStdinPacketOffset < packet.bytes.size) {
                    val count = Integer.min(length, packet.bytes.size - currentStdinPacketOffset)
                    System.arraycopy(packet.bytes, currentStdinPacketOffset, bytes, offset, count)
                    currentStdinPacketOffset += count
                    return count
                }
            }

            // We need a new packet, forget the old one and read a new one
            assert(currentStdinPacket.let { it == null || it.bytes.size == currentStdinPacketOffset })
            currentStdinPacket = null
            currentStdinPacketOffset = 0

            try {
                val newPacket = protocol.readPacket()
                when (newPacket.kind) {
                    ShellV2Protocol.PacketKind.STDIN -> {
                        currentStdinPacket = newPacket
                    }
                    ShellV2Protocol.PacketKind.CLOSE_STDIN -> {
                        return -1
                    }
                    else -> {
                        // TODO: Handle other type of packets
                    }
                }
            } catch (e: EOFException) {
                return -1
            }
        }
    }
}
