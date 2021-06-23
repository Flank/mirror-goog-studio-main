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
package com.android.tools.utp.plugins.host.additionaltestoutput

import com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfigProto.AndroidAdditionalTestOutputConfig
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A UTP plugin that retrieves additional test outputs from a test device into a host machine.
 */
class AndroidAdditionalTestOutputPlugin(private val logger: Logger = getLogger()) : HostPlugin {

    // Note: an empty companion object is required for getLogger().
    companion object {}

    lateinit var config: AndroidAdditionalTestOutputConfig

    override fun configure(config: Config) {
        config as ProtoConfig
        this.config = AndroidAdditionalTestOutputConfig.parseFrom(config.configProto!!.value)
    }

    override fun beforeAll(deviceController: DeviceController) {
        createEmptyDirectoryOnHost()
        createEmptyDirectoryOnDevice(deviceController)
    }

    /**
     * Creates an empty directory on host machine. If a directory exists at the given path,
     * it removes all contents in the directory.
     */
    private fun createEmptyDirectoryOnHost() {
        val dir = File(config.additionalOutputDirectoryOnHost)
        if(dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()
    }

    /**
     * Creates an empty directory on test device. If a directory exists at the given path,
     * it removes all contents in the directory.
     */
    private fun createEmptyDirectoryOnDevice(deviceController: DeviceController) {
        val dir = config.additionalOutputDirectoryOnDevice
        deviceController.deviceShellAndCheckSuccess("rm -rf \"${dir}\"")
        deviceController.deviceShellAndCheckSuccess("mkdir -p \"${dir}\"")
    }

    override fun beforeEach(testCase: TestCase?, deviceController: DeviceController) {
    }

    override fun afterEach(testResult: TestResult, deviceController: DeviceController): TestResult {
        // TODO: Reads testMetrics reported by am instrument command and create and add benchmark
        //       artifacts to TestResult. UTP's TestResult proto doesn't support testMetrics yet.
        //       Refer to DdmlibTestRunListenerAdapter for more details.
        return testResult
    }

    override fun afterAll(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController
    ): TestSuiteResult {
        try {
            // TODO: Currently, this plugin copies additional test outputs after all test cases.
            //       It can be moved to afterEach method and include them in the TestResult so that
            //       Android Studio can display them in TestMatrix streamingly.
            copyAdditionalTestOutputsFromDeviceToHost(deviceController)
        } catch (e: Exception) {
            logger.log(Level.WARNING, e) {
                "Failed to retrieve additional test outputs from device."
            }
        }
        return testSuiteResult
    }

    private fun copyAdditionalTestOutputsFromDeviceToHost(deviceController: DeviceController) {
        val deviceDir = config.additionalOutputDirectoryOnDevice
        val hostDir = File(config.additionalOutputDirectoryOnHost).absolutePath
        deviceController.deviceShellAndCheckSuccess("ls -1 \"${deviceDir}\"")
            .output
            .filter(String::isNotBlank)
            .forEach {
                deviceController.pull(TestArtifactProto.Artifact.newBuilder().apply {
                    destinationPathBuilder.path = "${deviceDir}/${it}"
                    sourcePathBuilder.path = "${hostDir}/${it}"
                }.build())
            }
    }

    override fun canRun(): Boolean = true

    override fun cancel(): Boolean = false

    private fun DeviceController.deviceShellAndCheckSuccess(vararg commands: String): CommandResult {
        val result = deviceShell(commands.toList())
        if (result.statusCode != 0) {
            logger.warning {
                "Shell command failed (${result.statusCode}): " +
                        "${commands.joinToString(" ")}\n" +
                        "${result.output.joinToString("\n")}"
            }
        }
        return result
    }
}
