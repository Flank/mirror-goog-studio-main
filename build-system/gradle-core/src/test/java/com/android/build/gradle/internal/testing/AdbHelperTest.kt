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

package com.android.build.gradle.internal.testing

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.repository.io.FileOpUtils
import com.android.testutils.MockitoKt.mock
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.nio.file.Path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class AdbHelperTest {
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var rootDir: Path
    private lateinit var adbExecutable: Path
    private lateinit var adbFilePath: String

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader

    @Mock
    private lateinit var logger: ILogger

    private lateinit var adbHelper: AdbHelper
    private val processCalls = mutableListOf<List<String>>()

    @Before
    fun setup() {
        rootDir = tmpFolder.newFolder().toPath()

        adbExecutable = rootDir.resolve("adb")
        adbFilePath = FileOpUtils.toFile(adbExecutable).absolutePath

        `when`(versionedSdkLoader.adbExecutableProvider)
            .thenReturn(
                FakeGradleProvider(FakeGradleRegularFile(FileOpUtils.toFile(adbExecutable)))
            )
    }

    @Test
    fun isBootCompleted_checksBothDevAndSys() {
        createAdbHelper()

        assertThat(adbHelper.isBootCompleted("deviceSerial", logger)).isFalse()

        assertThat(processCalls).hasSize(2)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "getprop", "sys.boot_completed")
        )
        assertThat(processCalls[1]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "getprop", "dev.bootcomplete")
        )
    }

    @Test
    fun isBootCompleted_checksSysShortCurcuits() {
        createAdbHelper("1")

        assertThat(adbHelper.isBootCompleted("deviceSerial", logger)).isTrue()

        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "getprop", "sys.boot_completed")
        )
    }

    @Test
    fun isBootCompleted_checkDevWorks() {
        createAdbHelper { processArgs ->
            if (processArgs.contains("dev.bootcomplete")) {
                "1"
            } else {
                "0"
            }
        }

        assertThat(adbHelper.isBootCompleted("deviceSerial", logger)).isTrue()

        assertThat(processCalls).hasSize(2)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "getprop", "sys.boot_completed")
        )
        assertThat(processCalls[1]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "getprop", "dev.bootcomplete")
        )
    }

    @Test
    fun isPackageManagerStarted_pmReady() {
        createAdbHelper("package: com.something...")

        assertThat(adbHelper.isPackageManagerStarted("deviceSerial")).isTrue()
        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "/system/bin/pm", "path", "android")
        )
    }

    @Test
    fun isPackageManagerStarted_pmNotReady() {
        createAdbHelper()

        assertThat(adbHelper.isPackageManagerStarted("deviceSerial")).isFalse()
        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "/system/bin/pm", "path", "android")
        )
    }

    @Test
    fun findDeviceSerialWithID_findsSerial() {
        createAdbHelper { processArgs ->
            when {
                processArgs.contains("devices") ->
                    """
                        list of devices attached
                        testDevice1 device
                        testDevice2 offline
                        testDevice3 device
                        testDevice4 device
                    """
                processArgs.contains("testDevice3") ->
                    """
                        SomeDeviceAndroidDebugTest
                        OK
                    """
                else ->
                    """
                        does_not_matter
                        OK
                    """
            }.trimIndent()
        }

        assertThat(adbHelper.findDeviceSerialWithId("SomeDeviceAndroidDebugTest"))
            .isEqualTo("testDevice3")

        assertThat(processCalls).hasSize(3)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "devices")
        )
        assertThat(processCalls[1]).isEqualTo(
            listOf(adbFilePath, "-s", "testDevice1", "emu", "avd", "id")
        )
        // testDevice2 is skipped as it is offline
        assertThat(processCalls[2]).isEqualTo(
            listOf(adbFilePath, "-s", "testDevice3", "emu", "avd", "id")
        )
        // testDevice4 is skipped as the id has been found.
    }

    @Test
    fun killDevice() {
        createAdbHelper()

        adbHelper.killDevice("testSerial")
        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "testSerial", "emu", "kill")
        )
    }

    private fun createAdbHelper(processOutput: String = "") {
        createAdbHelper {
            processOutput
        }
    }

    private fun createAdbHelper(processOutputFunc: (args: List<String>) -> String) {
        adbHelper = AdbHelper(FakeGradleProvider(versionedSdkLoader)) { argList ->
            mock<ProcessBuilder>().apply {
                `when`(start()).then {
                    processCalls.add(argList)
                    mock<Process>(withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS)).apply {
                        val processOutput = processOutputFunc(argList)
                        `when`(inputStream)
                            .thenReturn(ByteArrayInputStream(processOutput.toByteArray()))
                        `when`(errorStream)
                            .thenReturn(ByteArrayInputStream("".toByteArray()))
                    }
                }
            }
        }
    }
}
