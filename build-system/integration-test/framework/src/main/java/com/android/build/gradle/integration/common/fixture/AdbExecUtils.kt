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

fun uninstallPackage(packageName: String, ignoreErrors : Boolean = false) {
    execAdb(SdkHelper.getAdb().absolutePath, "uninstall", packageName, ignoreErrors = ignoreErrors)
}

fun installPackage(path: String, reinstall: Boolean = false, ignoreErrors: Boolean = false) {
    execAdb("install", if (reinstall) "-r" else "", path, ignoreErrors = ignoreErrors)
}

private fun execAdb(vararg  cmd: String, ignoreErrors: Boolean = false) {
    exec(SdkHelper.getAdb().absolutePath, *cmd, ignoreErrors = ignoreErrors)
}
private fun exec(vararg cmd: String, ignoreErrors : Boolean = false) {
    val exitCode = ProcessBuilder().command(*cmd).inheritIO().start().waitFor()
    if (!ignoreErrors) {
        Truth.assertWithMessage("Execution failed").that(exitCode).isEqualTo(0)
    }
}
