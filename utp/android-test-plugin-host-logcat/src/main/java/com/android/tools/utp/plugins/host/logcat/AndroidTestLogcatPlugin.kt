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
package com.android.tools.utp.plugins.host.logcat

import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import java.io.BufferedWriter
import java.io.File

/**
 * This plugin updates [TestSuiteResult] proto with logcat artifacts
 */
class AndroidTestLogcatPlugin : HostPlugin {
    private lateinit var outputDir: String
    private lateinit var tempLogcatFile: File
    private lateinit var tempLogcatWriter: BufferedWriter
    private lateinit var logcatCommandHandle: CommandHandle

    private val logger = getLogger()
    private var logcatFilePaths: MutableList<String> = mutableListOf()
    private var logcatOptions: List<String> = mutableListOf()

    override fun configure(config: Config) {
        outputDir = config.environment.outputDirectory
    }

    override fun beforeAll(deviceController: DeviceController) {
        logger.fine("Start logcat streaming.")
        logcatCommandHandle = startLogcatAsync(deviceController)
    }

    override fun beforeEach(
            testCase: TestCase?,
            deviceController: DeviceController
    ) {}

    override fun afterEach(
            testResult: TestResult,
            deviceController: DeviceController
    ): TestResult {
        val testCase = testResult.testCase
        val packageName = testCase.testPackage
        val className = testCase.testClass
        val methodName = testCase.testMethod
        val updatedTestResult = testResult.toBuilder().apply {
             logcatFilePaths.forEach {
                 if (it == generateLogcatFileName("$packageName.$className", methodName)) {
                     addOutputArtifact(
                             TestArtifactProto.Artifact.newBuilder().apply {
                                 labelBuilder.label = "logcat"
                                 labelBuilder.namespace = "android"
                                 sourcePathBuilder.path = it
                             }.build()
                     )
                 }
             }
         }.build()
        return updatedTestResult
    }

    override fun afterAll(
            testSuiteResult: TestSuiteResult,
            deviceController: DeviceController
    ): TestSuiteResult {
        stopLogcat()
        return testSuiteResult
    }

    override fun canRun(): Boolean = true

    override fun cancel(): Boolean = false

    /**
     * Generates the logcat file name using the output directory and test class and test method.
     */
    private fun generateLogcatFileName(
            testPackageAndClass: String,
            testMethod: String
    ) = File(outputDir, "logcat-$testPackageAndClass-$testMethod.txt").absolutePath

    /** Gets current date time on device. */
    private fun getDeviceCurrentTime(deviceController: DeviceController): String? {
        val dateCommandResult = deviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S"))
        if (dateCommandResult.statusCode != 0 || dateCommandResult.output.isEmpty()) {
            logger.warning("Failed to read device time.")
            return null
        }
        return "\'${dateCommandResult.output[0]}.000\'"
    }

    /** Set up logcat command args. */
    private fun setUpLogcatCommandLine(): List<String> {
        val logcatCommand = mutableListOf<String>()
        with(logcatCommand) {
            add("shell")
            add("logcat")
            add("-v")
            add("threadtime")
            add("-b")
            add("main")
            addAll(logcatOptions)
        }
        return logcatCommand
    }

    /** Start logcat streaming. */
    private fun startLogcatAsync(controller: DeviceController): CommandHandle {
        val deviceTime = getDeviceCurrentTime(controller)
        if (deviceTime != null) {
            logcatOptions = mutableListOf("-T", deviceTime)
        }
        var testRunInProgress = false
        return controller.executeAsync(setUpLogcatCommandLine()) { line ->
            if (line.contains("TestRunner: started: ")) {
                testRunInProgress = true
                parseLine(line)
            }
            if (testRunInProgress) {
                tempLogcatWriter.write(line)
                tempLogcatWriter.newLine()
                tempLogcatWriter.flush()
            }
            if (line.contains("TestRunner: finished:")) {
                testRunInProgress = false
            }
        }
    }

    /** Stop logcat stream. */
    private fun stopLogcat() {
        try {
            logcatCommandHandle.stop()
            logcatCommandHandle.waitFor() // Wait for the command to exit gracefully.
        } catch (t: Throwable) {
            logger.warning("Stopping logcat failed with the following error: $t")
        } finally {
            if (this::tempLogcatWriter.isInitialized) {
                tempLogcatWriter.close()
            }
        }
    }

    /**
     * Parse test package, class, and method info from logcat
     * Assumes that the line is of the form "**TestRunner: started: method(package.class)"
     */
    private fun parseLine(line: String) {
        val testPackageClassAndMethodNames = line.split("TestRunner: started: ")[1].split("(")
        val testMethod = testPackageClassAndMethodNames[0].trim()
        val testPackageAndClass = testPackageClassAndMethodNames[1].removeSuffix(")")
        val tempFileName = generateLogcatFileName(testPackageAndClass, testMethod)
        tempLogcatFile = File(tempFileName)
        logcatFilePaths.add(tempFileName)
        tempLogcatWriter = tempLogcatFile.outputStream().bufferedWriter()
    }
}
