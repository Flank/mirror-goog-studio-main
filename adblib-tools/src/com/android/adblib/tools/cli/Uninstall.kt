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
package com.android.adblib.tools.cli

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import com.android.adblib.tools.UninstallResult
import com.android.adblib.tools.uninstall
import kotlinx.coroutines.runBlocking

internal class Uninstall : DeviceCommand("uninstall")  {

    private fun printUsage() {
        println("Usage: uninstall ['FLAGS'] APPLICATION_ID")
    }

    override fun run(session : AdbLibSession, device : DeviceSelector, args : Arguments) : Boolean {
        val logger = thisLogger(session.host)
        val options : Array<String>
        val applicationID : String
        when (args.size()) {
            0 -> {
                printUsage()
                return false
            }
            else -> {
                options = args.consumeAll()
                applicationID = options.last()
                options.dropLast(1)
            }
        }

        // TODO: Refactor the way DeviceServices.uninstall works. It should throw an exception to be
        //       consistent with DeviceServices.install.
        var result : UninstallResult
        runBlocking {
            result = session.deviceServices.uninstall(device = device, applicationID, options.asList())
        }
        if (result.status != UninstallResult.Status.SUCCESS) {
            logger.warn(result.output)
        }
        return result.status == UninstallResult.Status.SUCCESS
    }

}
