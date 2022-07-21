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
package com.android.adblib.tools.debugging.packets

import com.android.adblib.tools.debugging.packets.JdwpCommands.CmdSet.SET_VM
import com.android.adblib.tools.debugging.packets.JdwpCommands.VmCmd.CMD_VM_ALLCLASSES
import org.junit.Assert.assertEquals
import org.junit.Test

class JdwpCommandsTest {

    @Test
    fun testCmdSetToStringWorks() {
        assertEquals("SET_VM", JdwpCommands.cmdSetToString(SET_VM.cmdSet))
    }

    @Test
    fun testCmdToStringWorks() {
        assertEquals("CMD_VM_ALLCLASSES", JdwpCommands.cmdToString(SET_VM.cmdSet, CMD_VM_ALLCLASSES.cmd))
    }
}
