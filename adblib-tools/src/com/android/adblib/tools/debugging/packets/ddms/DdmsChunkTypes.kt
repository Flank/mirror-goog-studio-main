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

internal class DdmsChunkTypes {
    @Suppress("SpellCheckingInspection")
    companion object {
        val HELO: Int = chunkTypeFromString("HELO")

        val FEAT: Int = chunkTypeFromString("FEAT")

        /**
         * "REAQ: REcent Allocation Query"
         */
        val REAQ: Int = chunkTypeFromString("REAQ")

        val APNM: Int = chunkTypeFromString("APNM")

        val WAIT: Int = chunkTypeFromString("WAIT")

        /**
         * Convert a 4-character string to a 32-bit chunk type.
         */
        fun chunkTypeFromString(type: String): Int {
            var result = 0
            check(type.length == 4) { "Type name must be 4 letter long" }
            for (i in 0..3) {
                result = result shl 8
                result = result or type[i].code.toByte().toInt()
            }
            return result
        }

        /**
         * Convert an integer type to a 4-character string.
         */
        fun chunkTypeToString(type: Int): String {
            val ascii = ByteArray(4)
            ascii[0] = (type shr 24 and 0xff).toByte()
            ascii[1] = (type shr 16 and 0xff).toByte()
            ascii[2] = (type shr 8 and 0xff).toByte()
            ascii[3] = (type and 0xff).toByte()
            return String(ascii, Charsets.US_ASCII)
        }
    }
}
