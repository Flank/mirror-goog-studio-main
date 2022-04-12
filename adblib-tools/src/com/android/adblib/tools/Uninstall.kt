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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbFeatures
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.utils.TextShellCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeoutException

class UninstallResult(val output: String) {

    val status: Status = when {
        (output == "Success") -> Status.SUCCESS
        else -> Status.FAILURE
    }

    enum class Status { SUCCESS, FAILURE }
}

/**
 * Uninstall an application identified by its applicationID [applicationID].
 *
 * Always use device 'pm' CLI over SHELL protocol.
 *
 * @param [device] the [DeviceSelector] corresponding to the target device
 * @param [userID] the user identifier
 * @param [applicationID] the application idenfier (usually a package formatted name).
 * @param [timeout] timeout tracking the command execution, tracking starts *after* the
 *   device connection has been successfully established. If the command takes more time than
 *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
 */
suspend fun AdbDeviceServices.uninstall(
    // The specific device to talk to via the service above
    device: DeviceSelector,
    // The userID on that device
    userID: Int = 0,
    // The applicationID on that device for that userID
    applicationID: String,
    // Timeout
    timeout : Duration = Duration.ofSeconds(10)
): UninstallResult {
    // TODO Improve perf by supporting 'cmd uninstall' and 'abb package uninstall' variants.
    var cmd = "pm uninstall --user $userID $applicationID"
    val flow = this.shell(device, cmd, TextShellCollector(), commandTimeout = timeout)
    val output = flow.first()
    return UninstallResult(output)
}
