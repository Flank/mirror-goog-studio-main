/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.profgen

import java.io.UTFDataFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Base-128 bit mask.  */
private const val MASK: Int = 0x7f

/**
 * Decodes an unsigned LEB128 to an Int
 *
 * LEB128 ("Little-Endian Base 128") is a variable-length encoding for arbitrary signed or unsigned
 * integer quantities. The format was borrowed from the DWARF3 specification. In a .dex file, LEB128
 * is only ever used to encode 32-bit quantities.
 *
 *
 * Each LEB128 encoded value consists of one to five bytes, which together represent a single
 * 32-bit value. Each byte has its most significant bit set except for the final byte in the
 * sequence, which has its most significant bit clear. The remaining seven bits of each byte are
 * payload, with the least significant seven bits of the quantity in the first byte, the next seven
 * in the second byte and so on. In the case of a signed LEB128 (sleb128), the most significant
 * payload bit of the final byte in the sequence is sign-extended to produce the final value. In the
 * unsigned case (uleb128), any bits not explicitly represented are interpreted as 0.
 */
internal val ByteBuffer.leb128: Int
    get() {
        var value = 0
        var b: Int
        var idx = 0
        do {
            b = ubyte
            value = value or (b and MASK shl idx++ * 7)
        } while (b and MASK.inv() != 0)
        return value
    }

/**
 * Modified UTF-8 as described in the dex file format spec.
 *
 *
 * Derived from libcore's MUTF-8 encoder at java.nio.charset.ModifiedUtf8.
 *
 * Decodes bytes from the ByteBuffer until a delimiter 0x00 is
 * encountered. Returns a new string containing the decoded characters.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteBuffer.mutf8(encodedSize: Int): String {
    val out = CharArray(encodedSize)
    var s = 0
    while (true) {
        val a = get().toChar()
        if (a.toInt() == 0) {
            return String(out, 0, s)
        }
        out[s] = a
        if (a < '\u0080') {
            s++
        } else if (a.toInt() and 0xe0 == 0xc0) {
            val b = ubyte
            if (b and 0xC0 != 0x80) {
                throw UTFDataFormatException("bad second byte")
            }
            out[s++] = (a.toInt() and 0x1F shl 6 or (b and 0x3F)).toChar()
        } else if (a.toInt() and 0xf0 == 0xe0) {
            val b = ubyte
            val c = ubyte
            if (b and 0xC0 != 0x80 || c and 0xC0 != 0x80) {
                throw UTFDataFormatException("bad second or third byte")
            }
            out[s++] = (a.toInt() and 0x0F shl 12 or (b and 0x3F shl 6) or (c and 0x3F)).toChar()
        } else {
            throw UTFDataFormatException("bad byte")
        }
    }
}

internal fun Long.toIntSaturated(): Int = when {
    this > Int.MAX_VALUE -> Int.MAX_VALUE
    this < Int.MIN_VALUE -> Int.MIN_VALUE
    else -> toInt()
}

@OptIn(ExperimentalUnsignedTypes::class)
internal val ByteBuffer.ushort: Int
    get() = short.toUShort().toInt()

@OptIn(ExperimentalUnsignedTypes::class)
internal val ByteBuffer.ubyte: Int
    get() = get().toUByte().toInt()

internal enum class Endian(
    val number: Int,
    val order: ByteOrder
) {
    LITTLE(0x12345678, ByteOrder.LITTLE_ENDIAN),
    BIG(0x78563412, ByteOrder.BIG_ENDIAN);
    companion object {
        fun forNumber(value: Int) = when (value) {
            LITTLE.number -> LITTLE
            BIG.number -> BIG
            else -> error("No Endian for number $value")
        }
    }
}