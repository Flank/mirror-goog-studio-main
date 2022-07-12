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
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readOptionalByte
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readOptionalInt
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readOptionalLengthPrefixedString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataParsing.readString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeInt
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeOptionalByte
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeOptionalInt
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeOptionalLengthPrefixedString
import com.android.adblib.tools.debugging.packets.ddms.ChunkDataWriting.writeString
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsHeloChunk.Companion.SERVER_PROTOCOL_VERSION
import com.android.adblib.utils.ResizableBuffer

internal data class DdmsHeloChunk(
    /**
     * The protocol version of this DDM command, currently always `1`
     * (i.e. [SERVER_PROTOCOL_VERSION])
     */
    val protocolVersion: Int,
    /**
     * The process ID, always valid
     */
    val pid: Int,
    /**
     * Some arbitrary JVM description/identifier.
     */
    val vmIdentifier: String,
    /**
     * Process name, often equal to [packageName], unless the package name
     * is changed in the application manifest.
     */
    val processName: String,
    /**
     * Returns the user ID, or `null` if not available
     */
    val userId: Int? = null,
    /**
     * ABI or `null`` if not available
     */
    val abi: String? = null,
    /**
     * JVM flags or `null`` if not available
     */
    val jvmFlags: String? = null,
    /**
     * Whether the process is debuggable using native debugger. Always [Boolean.false] if not available.
     */
    val isNativeDebuggable: Boolean = false,
    /**
     * Application package name, or `null` if not available
     */
    val packageName: String? = null
) {

    companion object {

        const val SERVER_PROTOCOL_VERSION = 1

        internal suspend fun parse(
            chunk: DdmsChunkView,
            workBuffer: ResizableBuffer = ResizableBuffer()
        ): DdmsHeloChunk {
            workBuffer.clear()
            chunk.payload.readRemaining(workBuffer)
            val buffer = workBuffer.afterChannelRead(0)

            // Version, pid, vm identifier and process name are always present
            buffer.order(DDMS_CHUNK_BYTE_ORDER)
            val version = readInt(buffer)
            val pid = readInt(buffer)
            val vmIdentifierLength = readInt(buffer)
            val processNameLength = readInt(buffer)
            val vmIdentifier = readString(buffer, vmIdentifierLength)
            val processName = readString(buffer, processNameLength)

            // UserID was added in 2012
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/d693dfa75b7a156898890014e7192a792314b757
            val userId = readOptionalInt(buffer)

            // ABI and VM Flags were added in 2014
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/e901dbdee242182e4c768edebebc5bc9cbf67563
            val abi = readOptionalLengthPrefixedString(buffer)
            val jvmFlags = readOptionalLengthPrefixedString(buffer)

            // nativeDebuggable was added in 2016:
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/b68bcbdfe755540f3c21186211d4d9d30d4d0c7a
            val nativeDebuggable = readOptionalByte(buffer) == 1

            // Package name was added in 2019:
            // https://cs.android.com/android/_/android/platform/frameworks/base/+/ab720ee1611da9fd4579d1adeb0acd6358b4f424
            val packageName = readOptionalLengthPrefixedString(buffer)

            // All done, return chunk
            return DdmsHeloChunk(
                version,
                pid,
                vmIdentifier,
                processName,
                userId,
                abi,
                jvmFlags,
                nativeDebuggable,
                packageName
            )
        }

        internal fun writePayload(
            buffer: ResizableBuffer,
            protocolVersion: Int,
            pid: Int,
            vmIdentifier: String,
            processName: String,
            userId: Int? = null,
            abi: String? = null,
            jvmFlags: String? = null,
            isNativeDebuggable: Boolean? = null,
            packageName: String? = null
        ) {
            writeInt(buffer, protocolVersion)
            writeInt(buffer, pid)
            writeInt(buffer, vmIdentifier.length)
            writeInt(buffer, processName.length)
            writeString(buffer, vmIdentifier)
            writeString(buffer, processName)

            writeOptionalInt(buffer, userId)
            writeOptionalLengthPrefixedString(buffer, abi)
            writeOptionalLengthPrefixedString(buffer, jvmFlags)
            writeOptionalByte(buffer, isNativeDebuggable?.let { if (it) 1 else 0 })
            writeOptionalLengthPrefixedString(buffer, packageName)
        }
    }
}
