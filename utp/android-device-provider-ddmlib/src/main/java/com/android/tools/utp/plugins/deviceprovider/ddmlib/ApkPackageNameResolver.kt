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

package com.android.tools.utp.plugins.deviceprovider.ddmlib

import com.google.testing.platform.lib.process.execute
import com.google.testing.platform.lib.process.inject.SubprocessComponent

/**
 * Resolves package names from APK file.
 */
class ApkPackageNameResolver(
    private val aaptPath: String,
    private val subprocessComponent: SubprocessComponent) {

    companion object {
        val packageNameRegex = "package:\\sname='(\\S*)'.*$".toRegex()
    }

    /**
     * Returns the package name of the given APK file. Returns null if it fails
     * to resolve package name.
     */
    fun getPackageNameFromApk(apkPath: String): String? {
        var packageName: String? = null
        subprocessComponent.subprocess().execute(
            listOf(aaptPath, "dump", "badging", apkPath),
            stdoutProcessor = {
                if (packageNameRegex.matches(it)) {
                    packageName = packageNameRegex.find(it)?.groupValues?.get(1)
                }
            }
        )
        return packageName
    }
}
