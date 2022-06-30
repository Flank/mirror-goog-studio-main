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

import com.android.adblib.utils.ResizableBuffer

internal object ChunkDataWriting {

    internal fun writeString(workBuffer: ResizableBuffer, value: String) {
        value.forEach {
            workBuffer.appendShort(it.code.toShort())
        }
    }

    internal fun writeLengthPrefixedString(workBuffer: ResizableBuffer, value: String) {
        workBuffer.appendInt(value.length)
        writeString(workBuffer, value)
    }

    internal fun writeOptionalLengthPrefixedString(workBuffer: ResizableBuffer, value: String?) {
        value?.also { writeLengthPrefixedString(workBuffer, value) }
    }

    internal fun writeByte(workBuffer: ResizableBuffer, value: Byte) {
        workBuffer.appendByte(value)
    }

    internal fun writeOptionalByte(workBuffer: ResizableBuffer, value: Byte?) {
        value?.also { workBuffer.appendByte(it) }
    }

    internal fun writeInt(workBuffer: ResizableBuffer, value: Int) {
        return workBuffer.appendInt(value)
    }

    internal fun writeOptionalInt(workBuffer: ResizableBuffer, value: Int?) {
        value?.also { workBuffer.appendInt(it) }
    }
}
