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

import java.nio.ByteOrder

object JdwpPacketConstants {
    /**
     * Per JDWP specification, all packets use big endian order.
     */
    val PACKET_BYTE_ORDER: ByteOrder = ByteOrder.BIG_ENDIAN

    /**
     * The number of bytes in the header common to all valid JDWP packets.
     */
    const val PACKET_HEADER_LENGTH = 11

    /**
     * The byte offset of the `length` of a JDWP packets (stored as a 32-bit integer)
     */
    const val PACKET_LENGTH_OFFSET = 0x00

    /**
     * The byte offset of the `id` of a JDWP packet (stored as a 32-bit integer)
     */
    const val PACKET_ID_OFFSET = 0x04

    /**
     * The byte offset of the `flag` field of a JDWP packet (stored as an 8-bit integer)
     */
    const val PACKET_FLAGS_OFFSET = 0x08

    /**
     * For `Command` packets only: The byte offset of the `command set`
     */
    const val PACKET_CMD_SET_OFFSET = 0x09

    /**
     * For `Command` packets only: The byte offset of the `command` of the `command set`.
     */
    const val PACKET_CMD_OFFSET = 0x0a

    /**
     * For `Reply` packets only: The byte offset of the error code
     */
    const val PACKET_ERROR_CODE_OFFSET = 0x09

    /**
     * The bit that of the `flags` field the defines if a JDWP packet is a command or a reply.
     */
    const val REPLY_PACKET_FLAG = 0x80
}
