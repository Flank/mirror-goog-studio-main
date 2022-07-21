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
package com.android.adblib.tools

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.DeviceSelector
import com.android.adblib.utils.TextShellCollector
import kotlinx.coroutines.flow.Flow

internal class PMPm(deviceServices: AdbDeviceServices) : PM(deviceServices) {

    val CMD = "pm"

    override suspend fun createSession(device: DeviceSelector, options: List<String>) : Flow<String> {
        val opts = options.joinToString(" ")
        val cmd = "$CMD install-create $opts".trim()
        return deviceService.shell(device, cmd, TextShellCollector())
    }

    override suspend fun streamApk(device: DeviceSelector, sessionID: String, apk: AdbInputChannel, filename: String, size: Long) : Flow<String>{
        val cmd = "$CMD install-write -S $size $sessionID $filename -"
        return deviceService.exec(device, cmd, TextShellCollector(), apk, shutdownOutput = false)
    }

    override suspend fun commit(device: DeviceSelector, sessionID: String) : Flow<String> {
        val cmd = "$CMD install-commit $sessionID"
        return deviceService.shell(device, cmd, TextShellCollector())
    }

    override suspend fun abandon(device: DeviceSelector, sessionID: String) : Flow<String>{
        val cmd = "$CMD install-abandon $sessionID"
        return deviceService.shell(device, cmd, TextShellCollector())
    }

    override suspend fun getStrategy(): String {
       return "pm"
    }
}
