/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import java.io.OutputStream

interface JdwpPacketHandler {

    /**
     * Interface for fake debugger to handle incoming JDWP packets
     *
     * @param device The device associated with the client
     * @param client The client associated with the connection
     * @param packet The packet that is being handled
     * @param oStream The stream to write the response to
     * @return If true the fake debugger should continue accepting packets, if false it should
     * terminate the session
     */
    fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: JdwpPacket,
        oStream: OutputStream
    ): Boolean
}

data class JdwpCommandId(val cmdSet: Int, val cmd: Int)

object JdwpCommands {
    enum class CmdSet(val cmdSet: Int, val cmdList: List<Cmd>) {
        SET_VM(1, VmCmd.values().asList());

        val value: Int
            get() = cmdSet
    }

    interface Cmd {

        val name: String
        val cmd: Int
        val value: Int
            get() = cmd
    }

    enum class VmCmd(override val cmd: Int) : Cmd {
        CMD_VM_VERSION(1),
        CMD_VM_CLASSESBYSIGNATURE(2),
        CMD_VM_ALLCLASSES(3),
        CMD_VM_ALLTHREADS(4),
        CMD_VM_TOPLEVELTHREADGROUPS(5),
        CMD_VM_DISPOSE(6),
        CMD_VM_IDSIZES(7),
        CMD_VM_SUSPEND(8),
        CMD_VM_RESUME(9),
        CMD_VM_EXIT(10),
        CMD_VM_CREATESTRING(11),
        CMD_VM_CAPABILITIES(12),
        CMD_VM_CLASSPATHS(13),
        CMD_VM_DISPOSEOBJECTS(14),
        CMD_VM_HOLDEVENTS(15),
        CMD_VM_RELEASEEVENTS(16),
        CMD_VM_CAPABILITIESNEW(17),
        CMD_VM_REDEFINECLASSES(18),
        CMD_VM_SETDEFAULTSTRATUM(19),
        CMD_VM_ALLCLASSESWITHGENERIC(20),
    }
}
