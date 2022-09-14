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

package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.utils.SdkHelper
import com.google.common.truth.Truth
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

fun uninstallPackage(packageName: String, ignoreErrors : Boolean = false) =
    execAdb("uninstall", packageName, ignoreErrors = ignoreErrors)

fun executeShellCommand(vararg cmd: String, ignoreErrors: Boolean = false)
    = execAdb("shell", *cmd, ignoreErrors = ignoreErrors)

private fun execAdb(vararg  cmd: String, ignoreErrors: Boolean = false) =
    exec(SdkHelper.getAdb().absolutePath, *cmd, ignoreErrors = ignoreErrors)

private fun exec(vararg cmd: String, ignoreErrors : Boolean = false) : String {
    val execTimeoutSeconds = 10L
    val process = ProcessBuilder().command(*cmd).start()
    val didFinish = process.waitFor(execTimeoutSeconds, TimeUnit.SECONDS)
    val exitCode = process.exitValue()

    if (!ignoreErrors) {
        Truth.assertWithMessage("Execution timed out.").that(didFinish).isEqualTo(true)
        val error = process.errorStream.bufferedReader(Charset.defaultCharset()).readText()
        Truth.assertWithMessage("Execution failed with error: $error").that(exitCode).isEqualTo(0)
    }
    return process.inputStream.bufferedReader(Charset.defaultCharset()).readText()
}
