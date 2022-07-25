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
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.repository.io.FileOpUtils
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import java.io.InputStream
import java.lang.ProcessBuilder
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(JUnit4::class)
class AdbHelperTest {

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var rootDir: Path
    private lateinit var adbExecutable: Path
    private lateinit var adbFilePath: String
    private lateinit var versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    private lateinit var processCalls: MutableList<List<String>>
    private lateinit var processBuilder: ProcessBuilder
    private lateinit var processBuildFunction: (List<String>) -> Process
    private lateinit var logger: ILogger

    private lateinit var adbHelper: AdbHelper


    @Before
    fun setup() {
        rootDir = tmpFolder.newFolder().toPath()

        adbExecutable = rootDir.resolve("adb")
        adbFilePath = FileOpUtils.toFile(adbExecutable).absolutePath

        logger = mock(ILogger::class.java)
        versionedSdkLoader = mock(SdkComponentsBuildService.VersionedSdkLoader::class.java).also {
            `when`(it.adbExecutableProvider)
                .thenReturn(FakeGradleProvider(FakeGradleRegularFile(FileOpUtils.toFile(adbExecutable))))
        }

        processBuilder = mock(ProcessBuilder::class.java, RETURNS_DEEP_STUBS).also {
            `when`(it.start()).thenAnswer {
                processBuildFunction(processCalls.last())
            }
        }

        processCalls = mutableListOf()
        adbHelper = AdbHelper(FakeGradleProvider(versionedSdkLoader)) { processArgList ->
            processCalls.add(processArgList)
            processBuilder
        }

        processBuildFunction = { _ ->
            mock(Process::class.java, RETURNS_DEEP_STUBS)
        }
    }

    @Test
    fun isBootCompleted_checksBothDevAndSys() {
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
        val successResult = getStreamFromResource(
            "com/android/build/gradle/internal/testing/adbshell_success.txt"
        )
        processBuildFunction = { _ ->
            mockProcessWithOutput(successResult)
        }

        assertThat(adbHelper.isBootCompleted("deviceSerial", logger)).isTrue()

        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "getprop", "sys.boot_completed")
        )
    }

    @Test
    fun isBootCompleted_checkDevWorks() {
        val successResult = getStreamFromResource(
            "com/android/build/gradle/internal/testing/adbshell_success.txt"
        )
        val failResult = getStreamFromResource(
            "com/android/build/gradle/internal/testing/adbshell_failure.txt"
        )
        processBuildFunction = { processArgs ->
            if (processArgs.contains("dev.bootcomplete")) {
                mockProcessWithOutput(successResult)
            } else {
                mockProcessWithOutput(failResult)
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
        val pmResult = getStreamFromResource(
            "com/android/build/gradle/internal/testing/packagemanager_output.txt"
        )
        processBuildFunction = { _ ->
            mockProcessWithOutput(pmResult)
        }

        assertThat(adbHelper.isPackageManagerStarted("deviceSerial")).isTrue()
        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "/system/bin/pm", "path", "android")
        )
    }

    @Test
    fun isPackageManagerStarted_pmNotReady() {
        assertThat(adbHelper.isPackageManagerStarted("deviceSerial")).isFalse()
        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "deviceSerial", "shell", "/system/bin/pm", "path", "android")
        )
    }

    @Test
    fun findDeviceSerialWithID_findsSerial() {
        val deviceListResult = getStreamFromResource(
            "com/android/build/gradle/internal/testing/device_list.txt"
        )
        val targetDeviceId = getStreamFromResource(
            "com/android/build/gradle/internal/testing/target_device.txt"
        )
        val notTargetId = getStreamFromResource(
            "com/android/build/gradle/internal/testing/not_target_device.txt"
        )

        processBuildFunction = { processArgs ->
            when {
                processArgs.contains("devices") ->
                    mockProcessWithOutput(deviceListResult)
                processArgs.contains("testDevice3") ->
                    mockProcessWithOutput(targetDeviceId)
                else ->
                    mockProcessWithOutput(notTargetId)
            }
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
        adbHelper.killDevice("testSerial")
        assertThat(processCalls).hasSize(1)
        assertThat(processCalls[0]).isEqualTo(
            listOf(adbFilePath, "-s", "testSerial", "emu", "kill")
        )
    }

    private fun mockProcessWithOutput(output: InputStream): Process {
        return mock(Process::class.java, RETURNS_DEEP_STUBS).also {
            `when`(it.getInputStream()).thenReturn(
                output
            )
        }
    }

    private fun getStreamFromResource(srcPath: String): InputStream =
        this.javaClass.classLoader.getResourceAsStream(srcPath)
}
