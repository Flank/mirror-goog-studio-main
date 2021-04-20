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

package com.android.tools.utp.plugins.deviceprovider.gradle

import com.google.testing.platform.lib.process.execute
import com.google.testing.platform.lib.process.inject.SubprocessComponent

/**
 * Component of the [GradleManagedAndroidDeviceLauncher] to handle calls to adb.
 */
class GradleAdbManagerImpl(private val subprocessComponent: SubprocessComponent) : GradleAdbManager {
    private lateinit var adbPath: String

    override fun configure(adbPath: String) {
        this.adbPath = adbPath
    }

    private fun getAdbIdArgs(serial: String): List<String> =
            listOf(
                    adbPath,
                    "-s",
                    serial,
                    "emu",
                    "avd",
                    "id"
            )

    private fun getAdbCloseArgs(serial: String): List<String> =
            listOf(
                    adbPath,
                    "-s",
                    serial,
                    "emu",
                    "kill"
            )

    /**
     * Returns the serials of all active devices on adb.
     */
    override fun getAllSerials(): List<String> {
        val serials = mutableListOf<String>()

        subprocessComponent.subprocess().execute(
                listOf(adbPath, "devices"),
                environment = System.getenv(),
                stdoutProcessor = { line ->
                    val trimmed = line.trim()
                    val values = trimmed.split("\\s+".toRegex())
                    // Looking for "<serial>    device"
                    if (values.size == 2 && values[1] == "device") {
                        serials.add(values[0])
                    }
                }
        )

        return serials
    }

    /**
     * Returns the id associated with the corresponding serial.
     */
    override fun getId(deviceSerial: String): String? {
        var id: String? = null
        subprocessComponent.subprocess().execute(
                args = getAdbIdArgs(deviceSerial),
                environment = System.getenv(),
                stdoutProcessor = { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && trimmed != "OK") {
                        id = trimmed
                    }
                }
        )
        return id
    }

    override fun closeDevice(deviceSerial: String) {
        subprocessComponent.subprocess().execute(
                args = getAdbCloseArgs(deviceSerial),
                environment = System.getenv()
        )
    }
}
