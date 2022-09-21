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

import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_HEADER_LENGTH
import java.nio.ByteBuffer

object JdwpPacketFactory {

    /**
     * Creates a [MutableJdwpPacket] wrapping a given DDM chunk [chunkType] and [chunkPayload].
     */
    fun createDdmsPacket(
        jdwpPacketId: Int,
        chunkType: Int,
        chunkPayload: ByteBuffer
    ): MutableJdwpPacket {
        // Serialize chunk into a ByteBuffer
        val serializedChunk =
            ByteBuffer.allocate(DDMS_CHUNK_HEADER_LENGTH + chunkPayload.remaining())
        serializedChunk.order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)
        serializedChunk.putInt(chunkType)
        serializedChunk.putInt(chunkPayload.remaining())
        serializedChunk.put(chunkPayload)
        serializedChunk.flip()

        // Create a JDWP command packet that wraps the ByteBuffer
        return MutableJdwpPacket.createCommandPacket(
            jdwpPacketId,
            DdmsPacketConstants.DDMS_CMD_SET,
            DdmsPacketConstants.DDMS_CMD,
            serializedChunk
        )
    }
}
