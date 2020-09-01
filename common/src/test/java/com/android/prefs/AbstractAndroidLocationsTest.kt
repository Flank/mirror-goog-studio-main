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
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.File

class AbstractAndroidLocationsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()

    @Test
    fun `ANDROID_PREFS_ROOT via sys prop`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(mapOf(AbstractAndroidLocations.ANDROID_PREFS_ROOT to testLocation.absolutePath))
        val logger = RecordingLogger()

        val locationProvider: AndroidLocationsProvider = AndroidLocations(provider, logger)
        val result = locationProvider.prefsLocation

        val expected = File(testLocation, ".android")
        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(expected)

        Truth.assertWithMessage("Emitted Warnings").that(logger.warnings).isEmpty()

        val result2 = locationProvider.prefsLocation
        Truth.assertWithMessage("Test Location2")
            .that(result2)
            .isEqualTo(expected)
        Truth.assertWithMessage("Test Location created")
            .that(result2.isDirectory)
            .isTrue()
    }

    @Test
    fun `ANDROID_SDK_HOME usage`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(mapOf("ANDROID_SDK_HOME" to testLocation.absolutePath))
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).prefsLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(File(testLocation, ".android"))
    }

    @Test
    fun `ANDROID_PREFS_ROOT and ANDROID_SDK_HOME with different values`() {
        val androidSdkHomeLocation = folder.newFolder().absolutePath
        val androidPrefsRootLocation = folder.newFolder().absolutePath
        val provider = FakeProvider(
            mapOf(
                "ANDROID_SDK_HOME" to androidSdkHomeLocation,
                "ANDROID_PREFS_ROOT" to androidPrefsRootLocation
            )
        )
        val logger = RecordingLogger()

        exceptionRule.expect(AndroidLocationsException::class.java)
        exceptionRule.expectMessage(
            """
                Both ANDROID_PREFS_ROOT and ANDROID_SDK_HOME are set to different values
                Support for ANDROID_SDK_HOME is deprecated. Use ANDROID_PREFS_ROOT only.
                Current values:
                ANDROID_SDK_ROOT: $androidPrefsRootLocation
                ANDROID_SDK_HOME: $androidSdkHomeLocation
                """.trimIndent()
        )

        AndroidLocations(provider, logger).prefsLocation
    }

    @Test
    fun `ANDROID_PREFS_ROOT and ANDROID_SDK_HOME with same values`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(
            mapOf(
                "ANDROID_SDK_HOME" to testLocation.absolutePath,
                "ANDROID_PREFS_ROOT" to testLocation.absolutePath
            )
        )
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).prefsLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(File(testLocation, ".android"))

        Truth.assertWithMessage("Warnings").that(logger.warnings).isEmpty()
    }

    @Test
    fun `ANDROID_SDK_HOME points to SDK`() {
        val testLocation = folder.newFolder()
        // create SDK folders under this
        FileUtils.mkdirs(File(testLocation, "platforms"))
        FileUtils.mkdirs(File(testLocation, "platform-tools"))

        val provider = FakeProvider(mapOf("ANDROID_SDK_HOME" to testLocation.absolutePath))
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).prefsLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
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

    @Test
    fun `No valid paths, no ANDROID_SDK_HOME`() {
        val provider = FakeProvider(
            sysProp = mapOf(
                AbstractAndroidLocations.ANDROID_PREFS_ROOT to "/path/to/android_prefs_root/sys-prop",
                "user.home" to "/path/to/user.home"
            ),
            envVar = mapOf(
                AbstractAndroidLocations.ANDROID_PREFS_ROOT to "/path/to/android_prefs_root/env-var",
                "TEST_TMPDIR" to "/path/to/TEST_TMPDIR",
                "HOME" to "/path/to/HOME"
            )
        )
        val logger = RecordingLogger()

        exceptionRule.expect(AndroidLocationsException::class.java)
        exceptionRule.expectMessage(
            """
                Unable to find the root location for the android preferences.
                The following locations have been checked, but they do not exist:
                - ANDROID_PREFS_ROOT(system property) -> /path/to/android_prefs_root/sys-prop
                - ANDROID_PREFS_ROOT(environment variable) -> /path/to/android_prefs_root/env-var
                - TEST_TMPDIR(environment variable) -> /path/to/TEST_TMPDIR
                - user.home(system property) -> /path/to/user.home
                - HOME(environment variable) -> /path/to/HOME
                """.trimIndent()
        )

        AndroidLocations(provider, logger).prefsLocation
    }

    @Test
    fun `No valid paths, with ANDROID_SDK_HOME`() {
        val provider = FakeProvider(
            sysProp = mapOf(
                "ANDROID_SDK_HOME" to "/path/to/android_sdk_home/sys-prop",
                "user.home" to "/path/to/user.home"
            ),
            envVar = mapOf(
                "ANDROID_SDK_HOME" to "/path/to/android_sdk_home/env-var",
                "TEST_TMPDIR" to "/path/to/TEST_TMPDIR",
                "HOME" to "/path/to/HOME"
            )
        )
        val logger = RecordingLogger()

        exceptionRule.expect(AndroidLocationsException::class.java)
        exceptionRule.expectMessage(
            """
                Unable to find the root location for the android preferences.
                The following locations have been checked, but they do not exist:
                - ANDROID_SDK_HOME(system property) -> /path/to/android_sdk_home/sys-prop
                - ANDROID_SDK_HOME(environment variable) -> /path/to/android_sdk_home/env-var
                - TEST_TMPDIR(environment variable) -> /path/to/TEST_TMPDIR
                - user.home(system property) -> /path/to/user.home
                - HOME(environment variable) -> /path/to/HOME
                """.trimIndent()
        )

        AndroidLocations(provider, logger).prefsLocation
    }

    @Test
    fun `No valid paths, both ANDROID_PREFS_ROOT and ANDROID_SDK_HOME`() {
        val provider = FakeProvider(
            sysProp = mapOf(
                AbstractAndroidLocations.ANDROID_PREFS_ROOT to "/path/to/.android/sys-prop",
                "ANDROID_SDK_HOME" to "/path/to/.android/sys-prop",
                "user.home" to "/path/to/user.home"
            ),
            envVar = mapOf(
                AbstractAndroidLocations.ANDROID_PREFS_ROOT to "/path/to/.android/env-var",
                "ANDROID_SDK_HOME" to "/path/to/.android/env-var",
                "TEST_TMPDIR" to "/path/to/TEST_TMPDIR",
                "HOME" to "/path/to/HOME"
            )
        )
        val logger = RecordingLogger()

        exceptionRule.expect(AndroidLocationsException::class.java)
        exceptionRule.expectMessage(
            """
                Unable to find the root location for the android preferences.
                The following locations have been checked, but they do not exist:
                - ANDROID_PREFS_ROOT(system property) -> /path/to/.android/sys-prop
                - ANDROID_SDK_HOME(system property) -> /path/to/.android/sys-prop
                - ANDROID_PREFS_ROOT(environment variable) -> /path/to/.android/env-var
                - ANDROID_SDK_HOME(environment variable) -> /path/to/.android/env-var
                - TEST_TMPDIR(environment variable) -> /path/to/TEST_TMPDIR
                - user.home(system property) -> /path/to/user.home
                - HOME(environment variable) -> /path/to/HOME
                """.trimIndent()
        )

        AndroidLocations(provider, logger).prefsLocation
    }

    @Test
    fun `Check failure to create location`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(mapOf(AbstractAndroidLocations.ANDROID_PREFS_ROOT to testLocation.absolutePath))
        val logger = RecordingLogger()

        val locationProvider: AndroidLocationsProvider = AndroidLocations(provider, logger)

        // write a file where it's expected that the folder is.
        val expected = File(testLocation, ".android")
        expected.writeText("foo")

        exceptionRule.expect(AndroidLocationsException::class.java)
        exceptionRule.expectMessage(
            """${expected.absolutePath} is not a directory!
This is the path of preference folder expected by the Android tools."""
        )

        locationProvider.prefsLocation
    }

    @Test
    fun `AVD Location inside prefsLocation`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(mapOf("ANDROID_PREFS_ROOT" to testLocation.absolutePath))
        val logger = RecordingLogger()

        val locationProvider = AndroidLocations(provider, logger)
        val prefsLocation = locationProvider.prefsLocation
        val expected = File(prefsLocation, "avd")

        val result = locationProvider.avdLocation
        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(expected)
    }

    @Test
    fun `AVD Location via ANDROID_AVD_HOME`() {
        val testLocation = folder.newFolder()
        val expectedAvdLocation = folder.newFolder()
        val provider = FakeProvider(
            mapOf(
                "ANDROID_PREFS_ROOT" to testLocation.absolutePath,
                "ANDROID_AVD_HOME" to expectedAvdLocation.absolutePath
            )
        )
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).avdLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(expectedAvdLocation)
    }

    @Test
    fun `userHomeLocation via TEST_TMPDIR`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(
            sysProp = mapOf(
                "user.home" to "overridden user.home"
            ),
            envVar = mapOf(
                "TEST_TMPDIR" to testLocation.absolutePath,
                "HOME" to "overriden HOME"
                )
        )
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).userHomeLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(testLocation)
    }

    @Test
    fun `userHomeLocation via USER_HOME`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(
            sysProp = mapOf(
                "user.home" to testLocation.absolutePath
            ),
            envVar = mapOf(
                "HOME" to "overriden HOME"
            )
        )
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).userHomeLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(testLocation)
    }

    @Test
    fun `userHomeLocation via HOME`() {
        val testLocation = folder.newFolder()
        val provider = FakeProvider(
            sysProp = mapOf(),
            envVar = mapOf(
                "HOME" to testLocation.absolutePath
            )
        )
        val logger = RecordingLogger()

        val result = AndroidLocations(provider, logger).userHomeLocation

        Truth.assertWithMessage("Test Location")
            .that(result)
            .isEqualTo(testLocation)
    }
}

internal class FakeProvider(
    private val sysProp: Map<String, String>,
    private val envVar: Map<String, String> = mapOf()
): EnvironmentProvider {
    override fun getSystemProperty(key: String): String? = sysProp[key]
    override fun getEnvVariable(key: String): String? = envVar[key]
}

internal class RecordingLogger: ILogger {
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

private class AndroidLocations(
    environmentProvider: EnvironmentProvider,
    logger: ILogger
): AbstractAndroidLocations(environmentProvider, logger, silent = true)
