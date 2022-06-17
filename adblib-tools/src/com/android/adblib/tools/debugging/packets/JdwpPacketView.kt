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

import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH

/**
 * Provides access to various elements of a JDWP packet. A JDWP packet always starts with
 * an 11-byte header, followed by variable size buffer of [packetLength] minus 11 bytes.
 */
interface JdwpPacketView {

    /**
     * The total number of bytes of this JDWP packet, including header (11 bytes) and [data].
     */
    val packetLength: Int

    /**
     * Packet unique identifier (4 bytes) with a given JDWP session
     */
    val packetId: Int

    /**
     * 8-bit flags of the packet, e.g. [JdwpPacketConstants.REPLY_PACKET_FLAG]
     */
    val packetFlags: Int

    /**
     * The "command set" identifier (1 byte) if the packet is a [isCommand] packet, or
     * throws [IllegalStateException] otherwise.
     */
    val packetCmdSet: Int

    /**
     * The "command" identifier (1 byte) within the [packetCmdSet] if the packet is a
     * [isCommand] packet, or throws [IllegalStateException] otherwise.
     */
    val packetCmd: Int

    /**
     * The "error code" if the packet is a [isReply] packet, or throws [IllegalStateException]
     * otherwise.
     */
    val errorCode: Int

    /**
     * [AdbBufferedInputChannel] to access the data associated to the packet. If the packet is valid,
     * [data] should contain exactly [packetLength] minus 11 bytes.
     */
    val data: AdbBufferedInputChannel

    /**
     * Returns `true` is the packet is a "command" packet matching the given [cmdSet] and [cmd]
     * values.
     */
    fun isCommand(cmdSet: Int, cmd: Int): Boolean {
        return isCommand && (packetCmdSet == cmdSet && packetCmd == cmd)
    }

    /**
     * Whether the packet is a "reply" packet (as opposed to a "command" packet)
     */
    val isReply: Boolean
        get() = (packetFlags and JdwpPacketConstants.REPLY_PACKET_FLAG) != 0

    /**
     * Whether the packet is a "command" packet (as opposed to a "reply" packet)
     */
    val isCommand: Boolean
        get() = !isReply

    /**
     * Returns `true` if the packet does not contain any [data].
     */
    val isEmpty: Boolean
        get() = packetLength == PACKET_HEADER_LENGTH
}

