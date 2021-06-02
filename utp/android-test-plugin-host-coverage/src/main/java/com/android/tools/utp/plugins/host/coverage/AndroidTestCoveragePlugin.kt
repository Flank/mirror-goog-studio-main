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
package com.android.tools.utp.plugins.host.coverage

import com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfigProto.AndroidTestCoverageConfig
import com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfigProto.AndroidTestCoverageConfig.TestCoveragePathOnDeviceCase
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
import com.google.testing.platform.runtime.android.controller.ext.isTestServiceInstalled
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.io.File
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A host plugin that retrieves test coverage data files (.ec) from a device
 * to a host machine.
 */
class AndroidTestCoveragePlugin(
    private val logger: Logger = getLogger(),
    private val createRandomId: () -> String = {
        UUID.randomUUID().toString()
    }) : HostPlugin {

    companion object {
        private const val TMP_DIR_ON_DEVICE: String = "/data/local/tmp/"

        // This constant value is a copy from
        // androidx.test.services.storage.TestStorageConstants.ON_DEVICE_PATH_INTERNAL_USE.
        // Android Test orchestrator outputs test coverage files under this directory when
        // useTestStorageService option is enabled.
        private const val TEST_STORAGE_SERVICE_OUTPUT_DIR = "/sdcard/googletest/internal_use/"
    }

    private lateinit var testCoverageConfig: AndroidTestCoverageConfig
    private var isTestServiceInstalled: Boolean = false

    override fun configure(config: Config) {
        config as ProtoConfig
        testCoverageConfig = AndroidTestCoverageConfig.parseFrom(
            config.configProto!!.value
        )
    }

    override fun beforeAll(deviceController: DeviceController) {
        createEmptyDirectoryOnHost(File(testCoverageConfig.outputDirectoryOnHost))
        cleanPreviousCodeCoverageOnDevice(deviceController)

        isTestServiceInstalled = deviceController.isTestServiceInstalled()
        if (isTestServiceInstalled) {
            val apiLevel = (deviceController.getDevice().properties as? AndroidDeviceProperties)
                ?.deviceApiLevel?.toIntOrNull() ?: 0
            if (apiLevel >= 30) {
                // Grant MANAGE_EXTERNAL_STORAGE permission to androidx.test.services so that it
                // can write test artifacts in external storage.
                deviceController.deviceShellAndCheckSuccess(
                    "appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow")
            }
        }
    }

    /**
     * Creates an empty directory. If a directory exists at the given path,
     * it removes all contents in the directory.
     */
    private fun createEmptyDirectoryOnHost(directory: File) {
        if(directory.exists()) {
            directory.deleteRecursively()
        }
        directory.mkdirs()
    }

    /**
     * Removes code coverages data on device from previous runs if exists.
     */
    private fun cleanPreviousCodeCoverageOnDevice(deviceController: DeviceController) {
        when(testCoverageConfig.testCoveragePathOnDeviceCase) {
            TestCoveragePathOnDeviceCase.SINGLE_COVERAGE_FILE -> {
                val file = testCoverageConfig.singleCoverageFile
                deviceController.deviceShellWithRunAs("rm -f \"${file}\"")
            }
            TestCoveragePathOnDeviceCase.MULTIPLE_COVERAGE_FILES_IN_DIRECTORY -> {
                val dir = testCoverageConfig.multipleCoverageFilesInDirectory
                deviceController.deviceShellWithRunAs("rm -rf \"${dir}\"")
                deviceController.deviceShellWithRunAs("mkdir -p \"${dir}\"")
            }
            else -> throw UnsupportedOperationException(
                "test_coverage_path_on_device must be specified.")
        }
    }

    override fun beforeEach(
        testCase: TestCase?,
        deviceController: DeviceController
    ) {}

    override fun afterEach(
        testResult: TestResult,
        deviceController: DeviceController
    ): TestResult = testResult

    override fun afterAll(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController
    ): TestSuiteResult {
        try {
            retrieveCoverageFiles(deviceController)
        } catch (e: Exception) {
            logger.log(Level.WARNING, e) {
                "Failed to retrieve code coverage data from device."
            }
        }
        return testSuiteResult
    }

    private fun retrieveCoverageFiles(deviceController: DeviceController) {
        val tmpDir = "${TMP_DIR_ON_DEVICE}${createRandomId()}-coverage_data"
        deviceController.deviceShellAndCheckSuccess("mkdir -p \"${tmpDir}\"")
        try {
            when (testCoverageConfig.testCoveragePathOnDeviceCase) {
                TestCoveragePathOnDeviceCase.SINGLE_COVERAGE_FILE ->
                    this::copyCoverageFileToHost
                TestCoveragePathOnDeviceCase.MULTIPLE_COVERAGE_FILES_IN_DIRECTORY ->
                    this::copyCoverageFilesInDirectoryToHost
                else -> throw UnsupportedOperationException(
                    "test_coverage_path_on_device must be specified.")
            }(deviceController, tmpDir)
        } finally {
            deviceController.deviceShellAndCheckSuccess("rm -rf \"${tmpDir}\"")
        }
    }

    private fun copyCoverageFileToHost(
        deviceController: DeviceController, tmpDir: String) {
        val coverageFilePath = if (isTestServiceInstalled) {
            "${TEST_STORAGE_SERVICE_OUTPUT_DIR}/${testCoverageConfig.singleCoverageFile}"
        } else {
            testCoverageConfig.singleCoverageFile
        }
        val coverageFileName = File(coverageFilePath).name
        val tmpCoverageFilePath = "${tmpDir}/${coverageFileName}"
        // We need to use "cat" instead of "cp" command to copy files to workaround
        // access permission problems. coverageFilePath is located in package
        // private directory so we need to run commands with "run-as" however
        // the tested application may not have access to the external storage (sdcard)
        // so "cp" command may fail with the access denied error. "adb shell" itself
        // has an access to the external storage.
        deviceController.deviceShellWithRunAs(
            "cat \"${coverageFilePath}\" > \"${tmpCoverageFilePath}\"")
        deviceController.pull(TestArtifactProto.Artifact.newBuilder().apply {
            destinationPathBuilder.path = tmpCoverageFilePath
            sourcePathBuilder.path = "${testCoverageConfig.outputDirectoryOnHost}/${coverageFileName}"
        }.build())
    }

    private fun copyCoverageFilesInDirectoryToHost(
        deviceController: DeviceController, tmpDir: String) {
        val coverageDir = if (isTestServiceInstalled) {
            "${TEST_STORAGE_SERVICE_OUTPUT_DIR}/${testCoverageConfig.multipleCoverageFilesInDirectory}"
        } else {
            testCoverageConfig.multipleCoverageFilesInDirectory
        }
        val covFileNames =
            deviceController.deviceShellWithRunAs("ls \"${coverageDir}\"").output.filter {
                it.endsWith(".ec")
            }.toList()
        covFileNames.forEach { covFileName ->
            val covFilePath = "${coverageDir}/${covFileName}"
            val tmpCovFilePath = "${tmpDir}/${covFileName}"
            deviceController.deviceShellWithRunAs("cat \"${covFilePath}\" > \"${tmpCovFilePath}\"")
            deviceController.pull(TestArtifactProto.Artifact.newBuilder().apply {
                destinationPathBuilder.path = tmpCovFilePath
                sourcePathBuilder.path = "${testCoverageConfig.outputDirectoryOnHost}/${covFileName}"
            }.build())
        }
    }

    override fun canRun(): Boolean = true

    override fun cancel(): Boolean = false

    /**
     * Executes adb shell command wrapped with run-as command if runAsPackageName
     * is not blank in [testCoverageConfig] and [isTestServiceInstalled] is false,
     * otherwise it executes the command as-is.
     *
     * When [isTestServiceInstalled] is true, test coverage file is always written under
     * non-package private directory ([TEST_STORAGE_SERVICE_OUTPUT_DIR]), so run-as
     * command is not needed (actually, you cannot use it as tested package may not have
     * access to sdcard).
     */
    private fun DeviceController.deviceShellWithRunAs(vararg commands: String): CommandResult {
        val runAsPackage = if (isTestServiceInstalled) {
            ""
        } else {
            testCoverageConfig.runAsPackageName
        }
        return if (runAsPackage.isNotBlank()) {
            deviceShellAndCheckSuccess(
                *commands.flatMap { c -> listOf("run-as", runAsPackage, c) }.toTypedArray()
            )
        } else {
            deviceShellAndCheckSuccess(*commands)
        }
    }

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
