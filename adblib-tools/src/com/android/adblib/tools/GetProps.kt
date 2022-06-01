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
import com.android.adblib.utils.TextShellCollector
import kotlinx.coroutines.flow.first

// TODO: Use ag/18624724 for getprop once it is available.
private val propRegex = Regex("""\[(\S*)\]: \[(\S*)\]""")
internal suspend fun getDeviceAPILevel(service : AdbDeviceServices, device: DeviceSelector) : Int {
    val flowProps = service.shell(device, "getprop", TextShellCollector())
    var rawProps = flowProps.first()

    val matches = propRegex.findAll(rawProps)
    try {
        matches.forEach {
            val (key, value) = it.destructured
            if (key == "ro.build.version.sdk") {
                return value.toInt()
            }
        }
    } catch (e : NumberFormatException) {
        return 1
    }

    return 1
}
