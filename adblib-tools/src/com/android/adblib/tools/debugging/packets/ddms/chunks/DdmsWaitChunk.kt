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
package com.android.adblib.tools.debugging.packets.ddms.chunks

import com.android.adblib.readRemaining
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readByte
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER
import com.android.adblib.utils.ResizableBuffer

internal data class DdmsWaitChunk(
    /**
     * The only currently supported value is `0`, with no particular meaning.
     */
    val reason: Byte
) {

    companion object {

        internal suspend fun parse(
            chunk: DdmsChunkView,
            workBuffer: ResizableBuffer = ResizableBuffer()
        ): DdmsWaitChunk {
            workBuffer.clear()
            chunk.data.readRemaining(workBuffer)
            val buffer = workBuffer.afterChannelRead(0)

            buffer.order(DDMS_CHUNK_BYTE_ORDER)
            val reason = readByte(buffer)

            // All done, return chunk
            return DdmsWaitChunk(reason)
        }

        internal fun writePayload(
            buffer: ResizableBuffer,
            reason: Byte
        ) {
            ChunkDataWriting.writeByte(buffer, reason)
        }
    }
}
