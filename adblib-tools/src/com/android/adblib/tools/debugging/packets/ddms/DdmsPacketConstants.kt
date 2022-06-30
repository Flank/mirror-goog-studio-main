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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_BYTE_ORDER
import com.android.adblib.tools.debugging.packets.JdwpPacketView

object DdmsPacketConstants {

    /**
     * Command set to use as the [JdwpPacketView.packetCmdSet]
     */
    const val DDMS_CMD_SET = 0xc7 // 'G' + 128

    /**
     * Command ID to use as the [JdwpPacketView.packetCmd]
     */
    const val DDMS_CMD = 0x01

    /**
     * # of bytes in a chunk header
     */
    const val DDMS_CHUNK_HEADER_LENGTH = 8 // 4-byte type, 4-byte len

    /**
     * DDMS packets use the same byte ordering as [JDWP packets][JdwpPacketView].
     */
    val DDMS_CHUNK_BYTE_ORDER = PACKET_BYTE_ORDER
}

