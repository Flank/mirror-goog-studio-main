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
import com.android.adblib.DeviceSelector
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Duration

/**
 * Install an application made of a list of apks path [apks].
 *
 * Always use device 'pm' CLI over SHELL protocol.
 *
 * @param [device] the [DeviceSelector] corresponding to the target device
 * @param [apks] the list of apks for this app
 * @param [options] the install options. e.g.: {"-t", "-r"}.
 * @param [timeout] the total amount of time allowed to install, including all sub-commands
 *
 * This function throws [InstallException] if there was an error reported by the installer on the
 * device.
 * This function throws [AdbProtocolErrorException], [AdbFailResponseException], or [IOException]
 * if there was a lower level communication error during execution of the underlying install commands.
 */

suspend fun AdbDeviceServices.install(
    device: DeviceSelector,
    apks : List<Path>,
    options : List<String> = listOf(),
    timeout : Duration = Duration.ofSeconds(120),
) {
    withContext(session.host.ioDispatcher) { // Make sure we NEVER run on EDT
        session.host.timeProvider.withErrorTimeout(timeout) {
            val client = PMDriver(this@install, device)
            client.install(apks, options)
        }
    }
}
