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
import com.android.adblib.tools.InstallException
import com.android.adblib.tools.install
import com.android.adblib.thisLogger
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class Install : DeviceCommand("install")  {

    private fun printUsage() {
        println("Usage: install [PM_FLAGS] APK,[APKs]")
    }

    override fun run(session: AdbLibSession, device : DeviceSelector, args: Arguments) : Boolean {
        val logger = thisLogger(session.host)
        val options = mutableListOf<String>()
        val apks = mutableListOf<Path>()

        if (!args.hasMore()) {
            printUsage()
            return false
        }

        while(args.hasMore()) {
            options.add(args.next())
        }

        // Search for apks from the end of the list.
        while(options.isNotEmpty()) {
            val path = Paths.get(options.last())
            if (Files.exists(path)) {
                apks.add(path)
                options.remove(options.last())
                continue
            }
            break
        }

        try {
            runBlocking {
                session.deviceServices.install(device, apks, options)
            }
        } catch (e : InstallException) {
            logger.warn(e, e.errorMessage)
            return false
        } catch (e : Exception) {
            logger.warn(e, e::class.qualifiedName + ": " + e.localizedMessage)
            return false
        }
        return true
    }
}
