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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

class DdmPacket private constructor(
    val id: Int,
    val errorCode: Short,
    val chunkType: Int,
    val payload: ByteArray,
    val isResponse: Boolean = false
) {

    @kotlin.jvm.Throws(IOException::class)
    fun write(outputStream: OutputStream) {
        JdwpPacket(
            id,
            isResponse,
            errorCode,
            getDdmPayload(chunkType, payload),
            DDMS_CMD_SET,
            DDMS_CMD
        ).write(outputStream)
    }

    companion object {

        val DDMS_CMD_SET = 0xc7

        val DDMS_CMD = 0x01

        @JvmStatic
        fun fromJdwpPacket(packet: JdwpPacket): DdmPacket {
            assert(packet.cmdSet == DDMS_CMD_SET && packet.cmd == DDMS_CMD)
            val buffer = ByteBuffer.wrap(packet.payload)
            val chunkType = buffer.int
            val chunkLength = buffer.int
            val ddmPayload = buffer.array()
            return DdmPacket(packet.id, packet.errorCode, chunkType, ddmPayload)
        }

        @JvmStatic
        fun create(chunkType: Int, payload: ByteArray) =
            DdmPacket(1234, 0.toShort(), chunkType, payload)

        @JvmStatic
        fun createResponse(id: Int, chunkType: Int, payload: ByteArray) =
            DdmPacket(id, 0.toShort(), chunkType, payload, isResponse = true)

        @JvmStatic
        fun isDdmPacket(packet: JdwpPacket): Boolean {
            return packet.cmdSet == DDMS_CMD_SET && packet.cmd == DDMS_CMD
        }

        @JvmStatic
        fun encodeChunkType(typeName: String): Int {
            assert(typeName.length == 4)
            var value = 0
            for (i in 0..3) {
                value = value shl 8
                value = value or typeName[i].code.toByte().toInt()
            }
            return value
        }

        private fun getDdmPayload(chunkType: Int, payload: ByteArray): ByteArray {
            val fullPayload = ByteArray(8 + payload.size) // 9 for chunkType and chunkLength
            val responseBuffer = ByteBuffer.wrap(fullPayload)
            responseBuffer.putInt(chunkType)
            responseBuffer.putInt(payload.size)
            responseBuffer.put(payload)
            return responseBuffer.array()
        }
    }
}
