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
import com.google.common.annotations.VisibleForTesting
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.io.File
import java.io.FileOutputStream

const val MANAGED_DEVICE_NAME_KEY = "gradleManagedDeviceDslName"

/**
 * A plugin to write device info in test results.
 */
class AndroidTestDeviceInfoPlugin : HostPlugin {
    private lateinit var outputDir: File
    private lateinit var deviceMemInfoFile: File
    private lateinit var deviceCpuInfoFile: File
    private lateinit var deviceInfoFile: File

    /**
     * Configures the plugin, updates the output directory.
     *
     * @param config: a configuration.
     */
    override fun configure(config: Config) {
        outputDir = File(config.environment.outputDirectory)
        deviceMemInfoFile = File(outputDir, "meminfo")
        deviceCpuInfoFile = File(outputDir, "cpuinfo")
        deviceInfoFile = File(outputDir, "device-info.pb")
    }

    /**
     * Writes device information files, which will be referenced by {@link #afterEach(TestResult)}.
     *
     * Currently we only have the SingleDeviceExecutor so all tests should share the same device
     * info files. We might need to rename those files in future once we have a MultiDeviceExecutor.
     *
     * @param deviceController: a device controller which controls an Android device.
     */
    override fun beforeAll(deviceController: DeviceController) {
        val deviceMemInfo = deviceController.deviceShell(listOf("cat", "/proc/meminfo")).output
        val deviceCpuInfo = deviceController.deviceShell(listOf("cat", "/proc/cpuinfo")).output

        // Write the files
        deviceMemInfoFile.printWriter().use { out ->
            deviceMemInfo.forEach {
                out.println(it)
            }
        }
        deviceCpuInfoFile.printWriter().use { out ->
            deviceCpuInfo.forEach {
                out.println(it)
            }
        }

        val device = deviceController.getDevice()
        val deviceProperties = device.properties as AndroidDeviceProperties
        val deviceSerial = device.serial
        val deviceAvdName = deviceProperties.avdName ?: ""
        val deviceName = if (deviceAvdName != "") {
            deviceAvdName
        } else {
            deviceSerial
        }
        val dslName = deviceProperties.map.getOrDefault(MANAGED_DEVICE_NAME_KEY, "")
        val deviceApiLevel = deviceProperties.deviceApiLevel
        val deviceRam = deviceMemInfo.getDeviceMemory()
        val deviceProcessors = deviceCpuInfo.getDeviceProcessors()
        val deviceAbis = deviceProperties.map["ro.product.cpu.abilist"]?.split(',')
        val deviceManufacturer = deviceProperties.map["ro.product.manufacturer"] ?: ""
        val deviceModel = deviceProperties.map["ro.product.model"] ?: ""

        val androidTestDeviceInfo = AndroidTestDeviceInfo.newBuilder()
                .setName(deviceName)
                .setApiLevel(deviceApiLevel)
                .setRamInBytes(deviceRam)
                .addAllProcessors(deviceProcessors)
                .addAllAbis(deviceAbis ?: emptyList())
                .setManufacturer(deviceManufacturer)
                .setSerial(deviceSerial)
                .setAvdName(deviceAvdName)
                .setGradleDslDeviceName(dslName)
                .setModel(deviceModel)
                .build()
        FileOutputStream(deviceInfoFile).use {
            androidTestDeviceInfo.writeTo(it)
        }
    }

    /** No-op */
    override fun beforeEach(
            testCase: TestCaseProto.TestCase?,
            deviceController: DeviceController
    ) = Unit

    /**
     * Updates device information artifacts in testResult. The device information was produced when
     * {@link #beforeAll(DeviceController)} was called.
     *
     * @param testResult: a base test result. Its copy will be returned with extra device
     *                    information artifacts.
     *
     * @return a copied TestResult with extra artifact, including "device-info.pb", "meminfo" and
     *         "cpuinfo".
     */
    override fun afterEach(
            testResult: TestResult,
            deviceController: DeviceController
    ): TestResult {
        return testResult.toBuilder().apply {
            addOutputArtifact(
                    TestArtifactProto.Artifact.newBuilder().apply {
                        labelBuilder.label = "device-info"
                        labelBuilder.namespace = "android"
                        sourcePathBuilder.path = deviceInfoFile.getPath()
                    }
            )
            addOutputArtifact(
                    TestArtifactProto.Artifact.newBuilder().apply {
                        labelBuilder.label = "device-info.meminfo"
                        labelBuilder.namespace = "android"
                        sourcePathBuilder.path = deviceMemInfoFile.getPath()
                    }
            )
            addOutputArtifact(
                    TestArtifactProto.Artifact.newBuilder().apply {
                        labelBuilder.label = "device-info.cpuinfo"
                        labelBuilder.namespace = "android"
                        sourcePathBuilder.path = deviceCpuInfoFile.getPath()
                    }
            )
        }.build()
    }

    /** No-op */
    override fun afterAll(
            testSuiteResult: TestSuiteResult,
            deviceController: DeviceController
    ): TestSuiteResult = testSuiteResult

    override fun canRun(): Boolean = true

    override fun cancel(): Boolean = false
}

private fun Double.fromKilobytesToLong() = (this * 1000L).toLong()
private fun Double.fromMegabytesToLong() = (this * 1000L * 1000L).toLong()
private fun Double.fromGigabytesToLong() = (this * 1000L * 1000L * 1000L).toLong()
private fun Double.fromTerabytesToLong() = (this * 1000L * 1000L * 1000L * 1000L).toLong()

// Parse memory from string. Return 0 if parser fails.
@VisibleForTesting
fun List<String>.getDeviceMemory(): Long {
    this.forEach {
        val (key, value) = it.split(':', ignoreCase = true, limit = 2) + listOf("", "")
        if (key.trim() == "MemTotal") {
            val (ramSize, unit) = value.trim().split(' ', ignoreCase = true, limit = 2)
            val ramSizeFloat = ramSize.toDoubleOrNull() ?: return 0
            // According to Wiki, kB means 1000 bytes and KB means 1024 bytes.
            // https://en.wikipedia.org/wiki/Kilobyte
            when (unit) {
                "kB" -> return ramSizeFloat.fromKilobytesToLong()
                "MB" -> return ramSizeFloat.fromMegabytesToLong()
                "GB" -> return ramSizeFloat.fromGigabytesToLong()
                "TB" -> return ramSizeFloat.fromTerabytesToLong()
                else -> return 0
            }
        }
    }
    // Return 0 if parser fails.
    return 0
}

@VisibleForTesting
fun List<String>.getDeviceProcessors(): Iterable<String> {
    val processors = mutableSetOf<String>()
    this.forEach {
        val (key, value) = it.split(':', ignoreCase = true, limit = 2) + listOf("", "")
        if (key.trim() == "model name") {
            processors.add(value.trim())
        }
    }
    return processors
}
