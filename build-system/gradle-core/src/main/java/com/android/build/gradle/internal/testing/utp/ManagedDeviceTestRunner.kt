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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.ide.common.process.JavaProcessExecutor
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.io.Files
import com.google.testing.platform.proto.api.core.TestStatusProto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Runs Android Instrumentation tests on a managed device through UTP (Unified Test Platform).
 */
class ManagedDeviceTestRunner(
    private val javaProcessExecutor: JavaProcessExecutor,
    private val utpDependencies: UtpDependencies,
    private val sdkComponents: SdkComponentsBuildService,
    private val retentionConfig: RetentionConfig,
    private val configFactory: UtpConfigFactory = UtpConfigFactory()) {

    fun runTests(
        managedDevice: UtpManagedDevice,
        outputDirectory: File,
        projectName: String,
        variantName: String,
        testData: StaticTestData,
        helperApks: Set<File>,
        logger: ILogger
    ): Boolean {
        val testApks = getTestApks(testData)
        val utpOutputDir = outputDirectory
        val utpTmpDir = Files.createTempDir()
        val utpTestLogDir = Files.createTempDir()
        val utpTestRunLogDir = Files.createTempDir()
        val runnerConfigProtoFile = File.createTempFile("runnerConfig", ".pb").also { file ->
            FileOutputStream(file).use { writer ->
                configFactory.createRunnerConfigProtoForManagedDevice(
                    managedDevice,
                    testData,
                    testApks.union(helperApks) + testData.testApk,
                    utpDependencies,
                    sdkComponents,
                    utpOutputDir,
                    utpTmpDir,
                    utpTestLogDir,
                    retentionConfig).writeTo(writer)
            }
        }
        val resultsProto = runUtpTestSuite(
            runnerConfigProtoFile,
            utpOutputDir,
            configFactory,
            utpDependencies,
            javaProcessExecutor,
            logger)
        resultsProto.writeTo(File(utpOutputDir, "test-result.pb").outputStream())

        try {
            FileUtils.deleteRecursivelyIfExists(utpTestLogDir)
            FileUtils.deleteRecursivelyIfExists(utpTestRunLogDir)
            FileUtils.deleteRecursivelyIfExists(utpTmpDir)
        } catch (e: IOException) {
            logger.warning("Failed to cleanup temporary directories: $e")
        }
        createTestReportXml(
            resultsProto,
            managedDevice.deviceName,
            projectName,
            variantName,
            logger,
            outputDirectory)
        return !resultsProto.testResultList.any { testCaseResult ->
            testCaseResult.testStatus == TestStatusProto.TestStatus.FAILED
                    || testCaseResult.testStatus == TestStatusProto.TestStatus.ERROR
        }
    }

    private fun getTestApks(testData: StaticTestData): List<File> {
        // TODO(b/141510559): need to find default apk or find best fit for the device category.
        return listOf()
    }
}
