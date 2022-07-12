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
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readInt
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER
import com.android.adblib.utils.ResizableBuffer

internal data class DdmsFeatChunk(val features: List<String>) {

    companion object {

        internal suspend fun parse(
            chunk: DdmsChunkView,
            workBuffer: ResizableBuffer = ResizableBuffer()
        ): DdmsFeatChunk {
            workBuffer.clear()
            chunk.payload.readRemaining(workBuffer)
            val buffer = workBuffer.afterChannelRead(0)

            buffer.order(DDMS_CHUNK_BYTE_ORDER)
            val count = readInt(buffer)
            val features = ArrayList<String>()
            for (i in 0 until count) {
                val length = readInt(buffer)
                val feature = readString(buffer, length)
                features.add(feature)
            }

            // All done, return chunk
            return DdmsFeatChunk(features)
        }

        internal fun writePayload(
            buffer: ResizableBuffer,
            features: List<String>
        ) {
            ChunkDataWriting.writeInt(buffer, features.size)
            features.forEach { feature ->
                ChunkDataWriting.writeLengthPrefixedString(buffer, feature)
            }
        }
    }
}
