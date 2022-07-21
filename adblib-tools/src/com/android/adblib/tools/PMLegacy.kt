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
import com.android.adblib.RemoteFileMode
import com.android.adblib.utils.TextShellCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

val INSTALL_APK_STAGING = "/data/local/tmp/adblib_tmp.apk"

internal class PMLegacy(deviceServices: AdbDeviceServices) : PM(deviceServices) {

    var streamed : Boolean = false
    var options : String = ""

    override suspend fun createSession(device: DeviceSelector, options: List<String>) : Flow<String> {
        this.options = options.joinToString(" ").trim()
        return flow{
            emit("Success: created install session [1986]")
        }
    }

    override suspend fun streamApk(device: DeviceSelector, sessionID: String, apk: AdbInputChannel, filename: String, size: Long) : Flow<String>{
        if (streamed) {
           throw IllegalStateException("Multiple APKs installation not supported on api < 20")
        }
        streamed = true

        // Push APK to device
        deviceService.sync(device).send(apk, INSTALL_APK_STAGING, RemoteFileMode.DEFAULT, null, null)

        // Install
        val parameters = mutableListOf<String>("pm", "install")
        if (options.isNotEmpty()) {
            parameters.add(options)
        }
        parameters.add(INSTALL_APK_STAGING)
        return deviceService.shell(device, parameters.joinToString(" "), TextShellCollector())
    }

    override suspend fun commit(device: DeviceSelector, sessionID: String) : Flow<String> {
        // Delete APK from device
        deleteTmpApk(device)

        return flow{
            emit("Success")
        }
    }

    override suspend fun abandon(device: DeviceSelector, sessionID: String) : Flow<String>{
        // Delete APK from device
        deleteTmpApk(device)

        return flow{
            emit("")
        }
    }

    private suspend fun deleteTmpApk(device: DeviceSelector) {
        deviceService.shell(device, "rm -f $INSTALL_APK_STAGING", TextShellCollector()).first()

    }

    override suspend fun getStrategy(): String {
        return "legacy"
    }
}
