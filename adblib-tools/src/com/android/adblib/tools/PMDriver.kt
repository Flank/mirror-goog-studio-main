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
import com.android.adblib.deviceProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

// A summary of how install works based on the target device.
// Three parameter are to be considered in order to decide of an install strategy.
//
// 1/ APKs location. They can be located either on the client side (where adb server runs) or
//    on the device side (usually in folder /data/local/tmp).
//
// 2/ PM version. The package manager is a binder service running on device. It evolved over API
//    changes from first supporting "install" to supporting "streamed install".
//        - "Install" only supports installing a single apk located on the device.
//        - "Streamed install" support installing apps made of several apks from either location.
//    * PM version is determined by API level.
//
// 3/ PM access. Initially the only ADB service supported was to spawn a "pm" executable using EXEC.
//    Later API introduced a "cmd" light-weight executable which is a generalized binder service
//    client ("pm" executable is limited to contacting "Package Manager" service and is heavy-weight).
//    "cmd" is also to be invoked over EXEC service. In API 30, a new ADB service "ABB" allowed to
//    contact binder services without need for spawning an executable. Two flavors of ABB exist,
//    one using cooked terminal (ABB) and one using raw terminal (ABB_EXEC).
//
//    * PM access is determined **NOT BY API LEVEL** by supported device/host features.
//
// Factoring in these three parameters, the install strategy algoritym is as follows.
//
// * If the device and adb host features mention "abb", ABB and ABB_EXEC service are used.
//   In this case "streamed install" is always used
// * ElseIf, the device and adb host features mention "cmd" then commands are sent to "cmd" over SHELL
//   service for install session management (create/abandon) and EXEC service for write. Whether
//   "install" or "multiple-install" is used is based on API.
// * Else fallback to single apk install. The APK is pushed to the device if needed and "pm"
//   executable is invoked (alike "cmd", using SHELL service and EXEC service). Whether
//   "install" or "multiple-install" is used is based on API.

// Currently supported: "Streamed install" over ABB_EXEC"
// TODO: Support for device located APKs.
// TODO: Add progress callback so that caller is notified of overall progress
//       (similar to AdbDeviceSyncServices.send )




internal class PMDriver(private val service : AdbDeviceServices, private val device: DeviceSelector) {

    private val logger = service.session.host.logger


    suspend fun install(
        apks: List<Path>,
        options: List<String> = listOf()
    ) {
        withContext(service.session.host.ioDispatcher){
           installOnIODispatcher(apks, options)
        }
    }

    private suspend fun installOnIODispatcher(
        apks: List<Path>,
        options: List<String> = listOf()
    ) {

        // Before we start, decide of an install strategy
        val features = service.session.hostServices.availableFeatures(device)
        val api = service.deviceProperties(device).api()
        val pm : PM = when {
            // Test API level
            api < 21 -> PMLegacy(service)
            features.contains("abb_exec") -> PMAbb(service)
            features.contains("cmd") -> PMCmd(service)
            else -> PMPm(service)
        }

        // Report our strategy
        val strategy = pm.getStrategy()
        logger.info{ "PMDriver installing via '$strategy' " }
        val optionsString = options.joinToString(" ")
        logger.info{ "  options: '$optionsString' " }
        apks.forEach{
            logger.info{ "  apk: '$it' " }
        }

        // Finally, installing!

        // 1/ Create session
        val flow = pm.createSession(device, options)
        val sessionID = parseSessionID(flow.first())

        try {

            // 2/ Write all apks
            apks.forEach { apk ->
                val size = Files.size(apk)
                // Make sure we have a filename that won't mess with our command
                val filename = cleanFilename(apk.fileName.toString())
                service.session.channelFactory.openFile(apk).use {
                    val flow = pm.streamApk(device, sessionID, it, filename, size)
                    parseInstallResult(flow.first())
                }
            }

            // 3/ Finalize
            val flow = pm.commit(device, sessionID)
            parseInstallResult(flow.first())
        } catch (t: Throwable) {
            runCatching {
                val flow = pm.abandon(device, sessionID)
                flow.first();
            }.onFailure { t.addSuppressed(it) }
            throw t
        }
    }

    companion object {
        // Parse output from package manager when issuing a request "install-create". A valid answer is
        // as follows:
        // "Success: created install session [1731367907]"
        // Error message vary from properly formatted output to Java StackTrace
        private val createSessionPattern = Pattern.compile("""Success: .*\[(\d*)\].*""")
        internal fun parseSessionID(output: String): String {
            val matcher: Matcher = createSessionPattern.matcher(output.trim())
            if (matcher.matches()) {
                return matcher.group(1)
            } else {
                throw InstallException(InstallResult(output))
            }
        }

        internal fun parseInstallResult(output: String) {
            val res = InstallResult(output)
            if (!res.success) {
                throw InstallException(res)
            }
        }




        // Replace everything not in A-Z, a-z, '_', '-', or '.' to '_'
        private val fileNameCleaner = "[^A-Za-z\\-_\\.-]".toRegex()
        internal fun cleanFilename(filename : String) : String =
            fileNameCleaner.replace(filename, "_")
    }
}
