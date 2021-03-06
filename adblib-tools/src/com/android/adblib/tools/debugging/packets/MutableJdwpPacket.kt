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

/**
 * A mutable version of [JdwpPacketView], to be used for creating JDWP packets
 * or re-using the same instance for multiple views over time for performance reason.
 */
class MutableJdwpPacket : JdwpPacketView {

    override var packetLength: Int = 0
        set(value) {
            if (value < JdwpPacketConstants.PACKET_HEADER_LENGTH) {
                throw IllegalArgumentException(
                    "Packet length should always be greater or equal " +
                            "to ${JdwpPacketConstants.PACKET_HEADER_LENGTH}"
                )
            }
            field = value
        }

    override var packetId: Int = 0

    override var packetFlags: Int = 0
        set(value) {
            if (value < 0 || value > 255) {
                throw IllegalArgumentException("Flags value should be with the [0..255] range")
            }
            field = value and 0xff
        }

    override var isReply: Boolean
        get() = (packetFlags and JdwpPacketConstants.REPLY_PACKET_FLAG) != 0
        set(value) {
            packetFlags = if (value) {
                packetFlags or JdwpPacketConstants.REPLY_PACKET_FLAG
            } else {
                packetFlags and JdwpPacketConstants.REPLY_PACKET_FLAG.inv()
            }
        }

    override var isCommand: Boolean
        get() = !isReply
        set(value) {
            isReply = !value
        }

    override var packetCmdSet: Int = 0
        get() {
            return if (isReply) {
                throw IllegalStateException("CmdSet is not available because JDWP packet is a reply packet")
            } else {
                field
            }
        }
        set(value) {
            if (value < 0 || value > 255) {
                throw IllegalArgumentException("CmdSet value $value should be with the [0..255] range")
            }
            field = value and 0xff
        }

    override var packetCmd: Int = 0
        get() {
            return if (isReply) {
                throw IllegalStateException("Cmd is not available because JDWP packet is a reply packet")
            } else {
                field
            }
        }
        set(value) {
            if (value < 0 || value > 255) {
                throw IllegalArgumentException("Cmd value should be with the [0..255] range")
            }
            field = value and 0xff
        }

    override var errorCode: Int = 0
        get() {
            return if (isCommand) {
                throw IllegalStateException("ErrorCode is not available because JDWP packet is a command packet")
            } else {
                field
            }
        }
        set(value) {
            if (value < 0 || value > 65535) {
                throw IllegalArgumentException("Cmd value should be with the [0..65535] range")
            }
            field = value and 0xffff
        }

    override var data = AdbBufferedInputChannel.empty()

    override fun toString(): String {
        return "JDWP packet(length=$packetLength, id=$packetId, flags=$packetFlags, " +
                if (isReply) "errorCode=$errorCode" else "cmdSet=$packetCmdSet, cmd=$packetCmd)"
    }
}
