/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.nativebuild

import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.AbiMatcher
import com.android.build.gradle.integration.common.utils.AndroidVersionMatcher
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.options.StringOption
import com.android.ddmlib.IDevice
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/** Assemble tests for renderscript with NDK mode enabled.  */
class RenderscriptNdkConnectedTest {
    private var testDevice: IDevice? = null
    private var targetAbi: String? = null

    companion object {
        @ClassRule
        @JvmField
        val emulator = getEmulator()
    }

    @get:Rule
    var adb = Adb()

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("renderscriptNdk")
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    private fun getDevice(matcher: Matcher<List<Abi>>) {
        // Find a device that supports 32 bits
        testDevice = adb.getDevice(
            AndroidVersionMatcher.anyAndroidVersion(),
            matcher)

        // Get the first ABI that matches in the device
        targetAbi = testDevice!!.abis.filter {
            matcher.matches(listOf(Abi.getByName(it)))
        }.getOrNull(0)
    }

    private fun testApk(testDevice: IDevice, targetAbi: String) {
        testDevice.installPackage(
            project.getApk(GradleTestProject.ApkType.DEBUG).file.toString(),
            true,
            "--abi $targetAbi")

        project
            .executor()
            .with(StringOption.DEVICE_POOL_SERIAL, testDevice.serialNumber)
            .run(GradleTestProject.DEVICE_TEST_TASK)
    }

    @Before
    fun setup() {
        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @After
    fun cleanup() {
        targetAbi = null

        testDevice = null
    }

    @Test
    @Ignore("b/165341811, b/175333004")
    fun testSeparateAbisFor32Bit() {
        getDevice(AbiMatcher.thirtyTwoBit())

        // abi should not be null
        assertThat(targetAbi).isNotNull()

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |android {
            |    ndkPath "${project.ndkPath}"
            |    defaultConfig {
            |        ndk {
            |            abiFilters "x86_64", "$targetAbi"
            |        }
            |    }
            |}""".trimMargin("|"))

        project.execute("clean", "assembleDebug")

        testApk(testDevice!!, targetAbi!!)
    }

    @Test
    fun testSeparateAbisFor64Bit() {
        // TODO(b/165341811, b/175333004): change this back to using abi filters

        targetAbi = "x86_64"

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |android {
            |    defaultConfig {
            |        ndk {
            |            abiFilters "$targetAbi", "x86"
            |        }
            |    }
            |}""".trimMargin("|"))

        project.executor().run("connectedAndroidTest")
    }

    @Test
    @Ignore("b/165341811, b/175333004")
    fun testOnly32BitAbi() {
        getDevice(AbiMatcher.thirtyTwoBit())

        // abi should not be null
        assertThat(targetAbi).isNotNull()

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |android {
            |    defaultConfig {
            |        ndk {
            |            abiFilters "$targetAbi"
            |        }
            |    }
            |}""".trimMargin("|"))

        project.execute("clean", "assembleDebug")

        testApk(testDevice!!, targetAbi!!)
    }

    @Test
    fun testOnly64BitAbi() {
        // TODO(b/165341811, b/175333004): change this back to using abi filters

        targetAbi = "x86_64"

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |android {
            |    defaultConfig {
            |        ndk {
            |            abiFilters "$targetAbi"
            |        }
            |    }
            |}""".trimMargin("|"))

        project.executor().run("connectedAndroidTest")
    }

    @Test
    @Ignore("b/165341811, b/175333004")
    fun testOldVersionApi() {
        getDevice(AbiMatcher.thirtyTwoBit())

        TestFileUtils.appendToFile(
            project.buildFile,
            """
            |android {
            |    defaultConfig {
            |        ndk {
            |            abiFilters "$targetAbi"
            |        }
            |    }
            |}""".trimMargin("|"))

        TestFileUtils.searchAndReplace(
            project.buildFile, "renderscriptTargetApi 28", "renderscriptTargetApi 20"
        )

        TestFileUtils.searchAndReplace(
            project.buildFile, "minSdkVersion 21", "minSdkVersion 20"
        )

        project.execute("clean", "assembleDebug")

        testApk(testDevice!!, targetAbi!!)
    }
}
