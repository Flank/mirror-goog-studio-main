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

import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidLocationTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()


    @Test
    fun `ANDROID_SDK_HOME usage warning`() {
        AndroidLocation.sPrefsLocation = null;

        val testLocation = folder.newFolder()
        val provider = FakeProvider(
            sysProp = mapOf("ANDROID_SDK_HOME" to testLocation.absolutePath),
            envVar = mapOf()
        )
        val logger = RecordingLogger()

        val result = AndroidLocation.getFolder(
            provider,
            logger
        )

        Truth.assertWithMessage("Test Location")
            .that(File(result))
            .isEqualTo(File(testLocation, ".android"))
    }

    @Test
    fun `ANDROID_PREFS_ROOT and ANDROID_SDK_HOME with different values`() {
        AndroidLocation.sPrefsLocation = null;

        val androidSdkHomeLocation = folder.newFolder().absolutePath
        val androidPrefsRootLocation = folder.newFolder().absolutePath
        val provider = FakeProvider(
            sysProp = mapOf(
                "ANDROID_SDK_HOME" to androidSdkHomeLocation,
                "ANDROID_PREFS_ROOT" to androidPrefsRootLocation
            ),
            envVar = mapOf()
        )
        val logger = RecordingLogger()

        exceptionRule.expect(AndroidLocation.AndroidLocationException::class.java)
        exceptionRule.expectMessage(
            """
                Both ANDROID_PREFS_ROOT and ANDROID_SDK_HOME are set to different values
                Support for ANDROID_SDK_HOME is deprecated. Use ANDROID_PREFS_ROOT only.
                Current values:
                ANDROID_SDK_ROOT: $androidPrefsRootLocation
                ANDROID_SDK_HOME: $androidSdkHomeLocation
                """.trimIndent()
        )

        AndroidLocation.getFolder(
            provider,
            logger
        )
    }

    @Test
    fun `ANDROID_PREFS_ROOT and ANDROID_SDK_HOME with same values`() {
        AndroidLocation.sPrefsLocation = null;

        val testLocation = folder.newFolder()
        val provider = FakeProvider(
            sysProp = mapOf(
                "ANDROID_SDK_HOME" to testLocation.absolutePath,
                "ANDROID_PREFS_ROOT" to testLocation.absolutePath
            ),
            envVar = mapOf()
        )
        val logger = RecordingLogger()

        val result = AndroidLocation.getFolder(
            provider,
            logger
        )

        Truth.assertWithMessage("Test Location")
            .that(File(result))
            .isEqualTo(File(testLocation, ".android"))

        Truth.assertWithMessage("Warnings").that(logger.warnings).isEmpty()
    }

    @Test
    fun `ANDROID_SDK_HOME points to SDK`() {
        AndroidLocation.sPrefsLocation = null;

        val testLocation = folder.newFolder()
        // create SDK folders under this
        FileUtils.mkdirs(File(testLocation, "platforms"))
        FileUtils.mkdirs(File(testLocation, "platform-tools"))

        val provider = FakeProvider(
            sysProp = mapOf("ANDROID_SDK_HOME" to testLocation.absolutePath),
            envVar = mapOf()
        )
        val logger = RecordingLogger()

        val result = AndroidLocation.getFolder(
            provider,
            logger
        )

        Truth.assertWithMessage("Test Location")
            .that(File(result))
            .isEqualTo(File(testLocation, ".android"))

        Truth.assertWithMessage("Emitted Warnings").that(logger.warnings).containsExactly(
            """
                ANDROID_SDK_HOME is set to the root of your SDK: $testLocation
                ANDROID_SDK_HOME is meant to be the path of the preference folder expected by the Android tools.
                It should NOT be set to the same as the root of your SDK.
                To set a custom SDK Location, use ANDROID_SDK_ROOT.
                If this is not set we default to: null""".trimIndent()
        )
    }

}
