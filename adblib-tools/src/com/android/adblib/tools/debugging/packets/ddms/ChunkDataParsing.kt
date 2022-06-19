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

import java.nio.ByteBuffer

internal object ChunkDataParsing {

    internal fun readString(chunk: ByteBuffer, len: Int): String {
        val data = CharArray(len)
        for (i in 0 until len) data[i] = chunk.char
        return String(data)
    }

    internal fun readByte(chunk: ByteBuffer): Byte {
        return chunk.get()
    }

    internal fun readInt(chunk: ByteBuffer): Int {
        return chunk.getInt()
    }

    internal fun readOptionalInt(chunk: ByteBuffer): Int? {
        return if (chunk.hasRemaining()) chunk.getInt() else null
    }

    internal fun readOptionalLengthPrefixedString(chunk: ByteBuffer): String? {
        return if (chunk.hasRemaining()) {
            val length = chunk.getInt()
            readString(chunk, length)
        } else {
            null
        }
    }

    internal fun readOptionalByte(chunk: ByteBuffer): Int? {
        return if (chunk.hasRemaining()) {
            chunk.get().toInt()
        } else {
            null
        }
    }
}

