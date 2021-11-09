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

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.testutils.MockitoKt.any
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
import java.io.File
import java.util.logging.Level
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * Unit tests for [UtpTestRunner].
 */
class UtpTestRunnerTest {
    @get:Rule var mockitoJUnitRule: MockitoRule =
            MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    @get:Rule var temporaryFolderRule = TemporaryFolder()

    @Mock lateinit var mockProcessExecutor: ProcessExecutor
    @Mock lateinit var mockWorkerExecutor: WorkerExecutor
    @Mock lateinit var mockWorkQueue: WorkQueue
    @Mock lateinit var mockExecutorServiceAdapter: ExecutorServiceAdapter
    @Mock lateinit var mockVersionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    @Mock lateinit var mockTestData: StaticTestData
    @Mock lateinit var mockAppApk: File
    @Mock lateinit var mockTestApk: File
    @Mock lateinit var mockHelperApk: File
    @Mock lateinit var mockDevice: DeviceConnector
    @Mock lateinit var mockCoverageDir: File
    @Mock lateinit var mockLogger: ILogger
    @Mock lateinit var mockUtpConfigFactory: UtpConfigFactory
    @Mock lateinit var mockRetentionConfig: RetentionConfig
    @Mock lateinit var mockTestResultListener: UtpTestResultListener
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockUtpDependencies: UtpDependencies
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockUtpTestResultListenerServerMetadata: UtpTestResultListenerServerMetadata

    private lateinit var resultsDirectory: File
    private lateinit var capturedRunnerConfigs: List<UtpRunnerConfig>

    @Before
    fun setupMocks() {
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockDevice.apiLevel).thenReturn(28)
        `when`(mockDevice.name).thenReturn("mockDeviceName")
        `when`(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        `when`(mockTestData.testedApkFinder).thenReturn { _, _ -> listOf(mockAppApk) }
        `when`(mockUtpConfigFactory.createRunnerConfigProtoForLocalDevice(
                any(),
                any(),
                anyIterable(),
                anyIterable(),
                anyIterable(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                nullable(File::class.java),
                anyInt(),
                any(),
                any(),
                any(),
                nullable(ShardConfig::class.java))).then {
            RunnerConfigProto.RunnerConfig.getDefaultInstance()
        }
        `when`(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())
    }

    private fun runUtp(result: UtpTestRunResult): Boolean {
        val runner = UtpTestRunner(
            null,
            mockProcessExecutor,
            mockWorkerExecutor,
            mockExecutorServiceAdapter,
            mockUtpDependencies,
            mockVersionedSdkLoader,
            mockRetentionConfig,
            useOrchestrator = false,
            uninstallIncompatibleApks = false,
            mockTestResultListener,
            Level.WARNING,
            mockUtpConfigFactory) { runnerConfigs, _, _, _, _ ->
            capturedRunnerConfigs = runnerConfigs
            listOf(result)
        }

        resultsDirectory = temporaryFolderRule.newFolder("results")
        return runner.runTests(
            "projectName",
            "variantName",
            mockTestData,
            setOf(mockHelperApk),
            listOf(mockDevice),
            0,
            setOf(),
            resultsDirectory,
            false,
            null,
            mockCoverageDir,
            mockLogger)
    }

    @Test
    fun runUtpAndPassed() {
        val result = runUtp(UtpTestRunResult(testPassed = true,
                                             TestSuiteResult.getDefaultInstance()))

        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isTrue()
        assertThat(File(resultsDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun runUtpAndFailed() {
        val result = runUtp(UtpTestRunResult(testPassed = false, null))

        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isFalse()
    }
}
