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
package com.android.adblib.tools.debugging

import com.android.adblib.tools.debugging.packets.JdwpCommands
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.JdwpPacketFactory
import java.nio.ByteBuffer

/**
 * Sends a [DDMS EXIT][DdmsChunkTypes.EXIT] [packet][DdmsChunkView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.sendDdmsExit(status: Int) {
    val buffer = ByteBuffer.allocate(4) // [pos = 0, limit =4]
    buffer.putInt(status) // [pos = 4, limit =4]
    buffer.flip()  // [pos = 0, limit =4]
    val packet = JdwpPacketFactory.createDdmsPacket(nextPacketId(), DdmsChunkTypes.EXIT, buffer)
    sendPacket(packet)
}

/**
 * Sends a [VM EXIT][JdwpCommands.VmCmd.CMD_VM_EXIT] command [packet][JdwpPacketView] to
 * the JDWP process corresponding to this [JDWP session][SharedJdwpSession].
 */
suspend fun SharedJdwpSession.sendVmExit(status: Int) {
    val buffer = ByteBuffer.allocate(4) // [pos = 0, limit = 4]
    buffer.putInt(status) // [pos = 4, limit = 4]
    buffer.flip()  // [pos = 0, limit = 4]

    val packet = MutableJdwpPacket.createCommandPacket(
        nextPacketId(),
        JdwpCommands.CmdSet.SET_VM.value,
        JdwpCommands.VmCmd.CMD_VM_EXIT.value,
        buffer
    )
    sendPacket(packet)
}
