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
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readOptionalInt
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readOptionalLengthPrefixedString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeLengthPrefixedString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeOptionalInt
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeOptionalLengthPrefixedString
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER
import com.android.adblib.utils.ResizableBuffer

internal data class DdmsApnmChunk(
    /**
     * Process name, often equal to [packageName], unless the package name
     * is changed in the application manifest.
     */
    val processName: String,
    /**
     * Returns the user ID, or `null` if not available
     */
    val userId: Int?,
    /**
     * Application package name, or `null` if not available
     */
    val packageName: String?
) {

    companion object {

        internal suspend fun parse(
            chunk: DdmsChunkView,
            workBuffer: ResizableBuffer = ResizableBuffer()
        ): DdmsApnmChunk {
            workBuffer.clear()
            chunk.data.rewind()
            chunk.data.readRemaining(workBuffer)
            val buffer = workBuffer.afterChannelRead(0)

            buffer.order(DDMS_CHUNK_BYTE_ORDER)
            val processNameLength = readInt(buffer)
            val processName = readString(buffer, processNameLength)

            // UserID was added in 2012
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/d693dfa75b7a156898890014e7192a792314b757
            val userId = readOptionalInt(buffer)

            // Newer devices (newer than user id support) send the package names associated with the app.
            // Package name was added in 2019:
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/ab720ee1611da9fd4579d1adeb0acd6358b4f424
            val packageName = readOptionalLengthPrefixedString(buffer)

            // All done, return chunk
            return DdmsApnmChunk(processName, userId, packageName)
        }

        internal fun writePayload(
            buffer: ResizableBuffer,
            processName: String,
            userId: Int?,
            packageName: String?
        ) {
            writeLengthPrefixedString(buffer, processName)
            writeOptionalInt(buffer, userId)
            writeOptionalLengthPrefixedString(buffer, packageName)
        }
    }
}
