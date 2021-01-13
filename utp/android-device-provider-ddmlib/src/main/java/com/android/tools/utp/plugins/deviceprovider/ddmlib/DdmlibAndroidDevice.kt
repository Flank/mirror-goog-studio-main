/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.utp.plugins.deviceprovider.ddmlib

import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties

/**
 * An implementation of [Device] using DDMLIB [IDevice].
 */
class DdmlibAndroidDevice(val ddmlibDevice: IDevice) : Device, IDevice by ddmlibDevice {
    override val port: Int? = null
    override val properties: AndroidDeviceProperties by lazy {
        val devicePropertyMap = mutableMapOf<String, String>()
        ddmlibDevice.executeShellCommand("printenv", object: MultiLineReceiver() {
            override fun isCancelled(): Boolean = false
            override fun processNewLines(lines: Array<out String>) {
                lines.forEach { line ->
                    val (key, value) = line.split("=", limit=2) + listOf("", "")
                    devicePropertyMap[key] = value
                }
            }
        })
        ddmlibDevice.executeShellCommand("getprop", object: MultiLineReceiver() {
            val regex = """\[(.+)\]: \[(.+)\]""".toRegex()
            override fun isCancelled(): Boolean = false
            override fun processNewLines(lines: Array<out String>) {
                lines.forEach { line ->
                    val matches = regex.find(line)?:return@forEach
                    val (key, value) = matches.destructured
                    devicePropertyMap[key] = value
                }
            }
        })
        AndroidDeviceProperties(
            devicePropertyMap,
            avdName = avdName
        )
    }

    override val serial: String
        get() = ddmlibDevice.serialNumber

    override val type: Device.DeviceType?
        get() = if (ddmlibDevice.isEmulator) {
            Device.DeviceType.VIRTUAL
        } else {
            Device.DeviceType.PHYSICAL
        }
}
