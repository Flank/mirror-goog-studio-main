/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.shellv2commandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellV2Protocol
import com.android.fakeadbserver.shellcommandhandlers.ShellHandler
import java.io.ByteArrayOutputStream
import java.lang.Integer.min

const val STDOUT_PACKET_SIZE = 80

/**
 * A [SimpleShellV2Handler] that outputs all characters received from `stdin` back to `stdout`,
 * one line at a time, i.e. characters are written back to `stdout` only when a newline ("\n")
 * character is received from `stdin`.
 */
class CatV2CommandHandler : SimpleShellV2Handler("cat") {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        protocol: ShellV2Protocol,
        device: DeviceState,
        args: String?
    ) {
        protocol.writeOkay()

        // Forward `stdin` packets back as `stdout` packets
        val stdinProcessor = StdinProcessor(protocol)
        while (true) {
            val packet = protocol.readPacket()
            when (packet.kind) {
                ShellV2Protocol.PacketKind.CLOSE_STDIN -> {
                    stdinProcessor.flush()
                    protocol.writeExitCode(0)
                    break
                }
                ShellV2Protocol.PacketKind.STDIN -> {
                    stdinProcessor.process(packet.bytes)
                }
                else -> {
                    // Ignore?
                }
            }
        }
    }

    class StdinProcessor(private val protocol: ShellV2Protocol) {
        val byteStream = ByteArrayOutputStream()

        fun flush() {
            if (byteStream.size() > 0) {
                protocol.writeStdout(byteStream.toByteArray())
                byteStream.reset()
            }
        }

        fun process(bytes: ByteArray) {
            // Send `stdout` packets of 200 bytes max. so simulate potentially custom
            // process on a "real" device
            var offset = 0
            while (offset < bytes.size) {
                val endIndex = min(offset + STDOUT_PACKET_SIZE, bytes.size)
                protocol.writeStdout(bytes.copyOfRange(offset, endIndex))
                offset = endIndex
            }
        }
    }
}
