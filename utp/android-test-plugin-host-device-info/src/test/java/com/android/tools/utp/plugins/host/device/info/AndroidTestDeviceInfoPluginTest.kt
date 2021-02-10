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

package com.android.tools.utp.plugins.host.device.info

import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto.AndroidTestDeviceInfo
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.api.config.ConfigBase
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.io.File
import java.io.FileInputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.nullable
import org.mockito.MockitoAnnotations.initMocks

/**
 * Unit tests for [AndroidTestDeviceInfoPlugin]
 */
@RunWith(JUnit4::class)
class AndroidTestDeviceInfoPluginTest {
    @get:Rule
    var tempFolder = TemporaryFolder()
    @Mock lateinit var mockDevice: Device
    @Mock lateinit var mockDeviceController: DeviceController
    @Mock lateinit var mockConfig: ConfigBase
    private lateinit var environment: Environment
    private lateinit var androidTestDeviceInfoPlugin: AndroidTestDeviceInfoPlugin
    private lateinit var emptyTestResult: TestResult
    private val deviceName = "totally_valid_device_name"
    private val deviceRamInKilobytes = 2L * 1000L * 1000L
    private val deviceRamString = "MemTotal: $deviceRamInKilobytes kB"
    private val deviceProcessor = "Virtual processor"
    private val deviceProcessorString = "model name : " + deviceProcessor
    private val deviceManufacturer = "Google"
    private val deviceAbis = listOf("x64_86", "x86")
    private val androidDeviceProperty = AndroidDeviceProperties(
            map = mapOf(
                    "ro.product.manufacturer" to deviceManufacturer,
                    "ro.product.cpu.abilist" to deviceAbis.joinToString(",")
            ),
            deviceApiLevel = "28"
    )

    @Before
    fun setup() {
        initMocks(this)
        environment = Environment(tempFolder.getRoot().getPath(), "", "", "", null)
        `when`(mockDeviceController.deviceShell(anyList(), nullable(Long::class.java))).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val command = (it.arguments[0] as List<String>).joinToString(separator = " ")
            if (command == "shell cat /proc/meminfo") {
                return@thenAnswer CommandResult(0, listOf(deviceRamString))
            } else if (command == "shell cat /proc/cpuinfo") {
                return@thenAnswer CommandResult(0, listOf(deviceProcessorString))
            }
            CommandResult(0, listOf(""))
        }
        `when`(mockDeviceController.getDevice()).thenReturn(mockDevice)
        `when`(mockDevice.properties).thenReturn(androidDeviceProperty)
        `when`(mockDevice.serial).thenReturn(deviceName)
        `when`(mockConfig.environment).thenReturn(environment)
        emptyTestResult = TestResult.newBuilder().build()
        androidTestDeviceInfoPlugin = AndroidTestDeviceInfoPlugin()
    }

    @Test
    fun afterEach_AddsDeviceInfo() {
        androidTestDeviceInfoPlugin.configure(mockConfig)
        androidTestDeviceInfoPlugin.beforeAll(mockDeviceController)
        androidTestDeviceInfoPlugin.beforeEach(TestCaseProto.TestCase.getDefaultInstance(), mockDeviceController)
        val testResult = androidTestDeviceInfoPlugin.afterEach(emptyTestResult, mockDeviceController)
        // Check artifact labels.
        assertThat(testResult.outputArtifactList).isNotEmpty()
        var numDeviceInfoProtos = 0
        testResult.outputArtifactList.forEach { artifact ->
            assertThat(artifact.label.namespace).isEqualTo("android")
            val file = File(artifact.sourcePath.path)
            assertThat(file.exists()).isTrue()
            if (artifact.label.label == "device-info") {
                numDeviceInfoProtos += 1
                // Validate protobuf content
                FileInputStream(file).use {
                    val deviceInfo = AndroidTestDeviceInfo.parseFrom(it)
                    assertThat(deviceInfo.name).isEqualTo(deviceName)
                    assertThat(deviceInfo.apiLevel).isEqualTo(androidDeviceProperty.deviceApiLevel)
                    assertThat(deviceInfo.ramInBytes).isEqualTo(deviceRamInKilobytes * 1000L)
                    assertThat(deviceInfo.processorsList).isEqualTo(listOf(deviceProcessor))
                    assertThat(deviceInfo.abisList).isEqualTo(deviceAbis)
                    assertThat(deviceInfo.manufacturer).isEqualTo(deviceManufacturer)
                    assertThat(deviceInfo.gradleDslDeviceName).isEqualTo("")
                }
            }
        }
        assertThat(numDeviceInfoProtos).isEqualTo(1)
    }

    @Test
    fun afterEach_AddsDeviceInfoWithManagedDevice() {
        val managedDeviceProperties = AndroidDeviceProperties(
            map = mapOf(
                    "gradleManagedDeviceDslName" to "device1",
                    "ro.product.manufacturer" to deviceManufacturer,
                    "ro.product.cpu.abilist" to deviceAbis.joinToString(",")
            ),
            deviceApiLevel = "28"
        )
        `when`(mockDevice.properties).thenReturn(managedDeviceProperties)

        androidTestDeviceInfoPlugin.configure(mockConfig)
        androidTestDeviceInfoPlugin.beforeAll(mockDeviceController)
        androidTestDeviceInfoPlugin.beforeEach(TestCaseProto.TestCase.getDefaultInstance(), mockDeviceController)
        val testResult = androidTestDeviceInfoPlugin.afterEach(emptyTestResult, mockDeviceController)
        // Check artifact labels.
        assertThat(testResult.outputArtifactList).isNotEmpty()
        var numDeviceInfoProtos = 0
        testResult.outputArtifactList.forEach { artifact ->
            assertThat(artifact.label.namespace).isEqualTo("android")
            val file = File(artifact.sourcePath.path)
            assertThat(file.exists()).isTrue()
            if (artifact.label.label == "device-info") {
                numDeviceInfoProtos += 1
                // Validate protobuf content
                FileInputStream(file).use {
                    val deviceInfo = AndroidTestDeviceInfo.parseFrom(it)
                    assertThat(deviceInfo.name).isEqualTo(deviceName)
                    assertThat(deviceInfo.apiLevel).isEqualTo(androidDeviceProperty.deviceApiLevel)
                    assertThat(deviceInfo.ramInBytes).isEqualTo(deviceRamInKilobytes * 1000L)
                    assertThat(deviceInfo.processorsList).isEqualTo(listOf(deviceProcessor))
                    assertThat(deviceInfo.abisList).isEqualTo(deviceAbis)
                    assertThat(deviceInfo.manufacturer).isEqualTo(deviceManufacturer)
                    assertThat(deviceInfo.gradleDslDeviceName).isEqualTo("device1")
                }
            }
        }
        assertThat(numDeviceInfoProtos).isEqualTo(1)
    }

    @Test
    fun canRun_IsTrue() {
        assertThat(androidTestDeviceInfoPlugin.canRun()).isTrue()
    }

    @Test
    fun parseMemInKb() {
        val memSizeInKb = 128L
        val memInfo = listOf("header", "MemTotal: $memSizeInKb kB", "footer")
        assertThat(memInfo.getDeviceMemory()).isEqualTo(memSizeInKb * 1000)
    }

    @Test
    fun parseMemInMb() {
        val memSizeInMb = 128L
        val memInfo = listOf("header", "MemTotal: $memSizeInMb MB", "footer")
        assertThat(memInfo.getDeviceMemory()).isEqualTo(memSizeInMb * 1000 * 1000)
    }

    @Test
    fun parseMemInGb() {
        val memSizeInGb = 128L
        val memInfo = listOf("header", "MemTotal: $memSizeInGb GB", "footer")
        assertThat(memInfo.getDeviceMemory()).isEqualTo(memSizeInGb * 1000L * 1000L * 1000L)
    }

    @Test
    fun parseMemInTb() {
        val memSizeInTb = 128L
        val memInfo = listOf("header", "MemTotal: $memSizeInTb TB", "footer")
        assertThat(memInfo.getDeviceMemory()).isEqualTo(memSizeInTb * 1000L * 1000L * 1000L * 1000L)
    }

    @Test
    fun parseBadMem() {
        val memInfo = listOf("header", "footer")
        assertThat(memInfo.getDeviceMemory()).isEqualTo(0)
    }

    @Test
    fun parseProcessors() {
        val cpuInfo = listOf(
                "header",
                "model name : cpu_type_a",
                "model name : cpu_type_b",
                "model name : cpu_type_a",
                "footer"
        )
        val processors = cpuInfo.getDeviceProcessors()
        assertThat(processors.toSet()).isEqualTo(setOf("cpu_type_a", "cpu_type_b"))
    }

    @Test
    fun parseEmptyProcessors() {
        val cpuInfo = listOf(
                "header",
                "footer"
        )
        val processors = cpuInfo.getDeviceProcessors()
        assertThat(processors).isEmpty()
    }
}
