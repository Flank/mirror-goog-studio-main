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
package com.android.prefs

import com.android.utils.EnvironmentProvider
import com.android.utils.ILogger
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidLocationTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun testAndroidSdkHome() {
        val provider = FakeProvider(mapOf("ANDROID_SDK_HOME" to folder.newFolder().absolutePath))
        val logger = RecordingLogger()

        AndroidLocation.getFolder(
            provider,
            logger
        )

        Truth.assertWithMessage("Emitted Warnings").that(logger.warnings).containsExactly(
            """Using ANDROID_SDK_HOME for the location of the '.android' preferences location is deprecated, please use ${AndroidLocation.ANDROID_PREFS_ROOT} instead.
Support for ANDROID_SDK_HOME will be removed in 6.0"""
        )
    }
}

private class FakeProvider(
    private val map: Map<String, String>
): EnvironmentProvider {
    override fun getSystemProperty(key: String): String? = map[key]
    override fun getEnvVariable(key: String): String? = map[key]
}

private class RecordingLogger: ILogger {
    val warnings = mutableListOf<String>()

    override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) {
        throw RuntimeException("Unexpected call to errors()")
    }

    override fun warning(msgFormat: String, vararg args: Any?) {
        warnings.add(String.format(msgFormat, *args))
    }

    override fun info(msgFormat: String, vararg args: Any?) {
        throw RuntimeException("Unexpected call to info()")
    }

    override fun verbose(msgFormat: String, vararg args: Any?) {
        throw RuntimeException("Unexpected call to verbose()")
    }
}
