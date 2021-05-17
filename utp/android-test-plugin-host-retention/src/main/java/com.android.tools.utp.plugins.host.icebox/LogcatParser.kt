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

package com.android.tools.utp.plugins.host.icebox

import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.runtime.android.controller.ext.deviceShell

class LogcatParser {

    private val regex =
        "((-|:|\\d)+)\\s+((-|:|\\d|[.])+)\\s+(\\d+)\\s+(\\d+)\\s+(\\w+)\\s+(\\w+): (.+)".toRegex()
    private val command = listOf(
        "shell", "logcat", "-v", "threadtime", "-b", "main"
    )
    private var executor: CommandHandle? = null

    // Start parsing logcat commands.
    // The parameters of the callback function are:
    //   Date, time, PID, TID, verbosity level, tag, message
    fun start(
        deviceController: DeviceController,
        processor: (
            date: String, time: String, pid: String, uid: String, verbosity: String, tag: String,
            message: String
        ) -> Unit
    ) {
        stop()
        val deviceTime = getDeviceCurrentTime(deviceController)
        val fullCommand = command.toMutableList()
        if (deviceTime != null) {
            fullCommand.add("-T")
            fullCommand.add(deviceTime)
        }
        executor = deviceController.executeAsync(fullCommand) {
            regex.matchEntire(it)?.destructured
                ?.let { (date, _, time, _, pid, uid, verbosity, tag, message) ->
                    processor(date, time, pid, uid, verbosity, tag, message)
                }
        }
    }

    /** Gets current date time on device. */
    private fun getDeviceCurrentTime(deviceController: DeviceController): String? {
        val dateCommandResult = deviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S"))
        if (dateCommandResult.statusCode != 0 || dateCommandResult.output.isEmpty()) {
            return null
        }
        return "\'${dateCommandResult.output[0]}.000\'"
    }

    fun stop() {
        if (executor != null) {
            executor?.stop()
            executor?.waitFor()
            executor = null
        }
    }
}
