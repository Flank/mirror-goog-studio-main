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

package com.android.build.gradle.internal.testing.utp

import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.ManagedVirtualDeviceLockManager
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.testutils.MockitoKt.any
import com.android.testutils.SystemPropertyOverrides
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.Environment
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
import java.io.File
import java.util.logging.Level
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [ManagedDeviceTestRunner].
 */
class ManagedDeviceTestRunnerTest {
    @get:Rule var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()
    @get:Rule var temporaryFolderRule = TemporaryFolder()

    @Mock lateinit var mockWorkerExecutor: WorkerExecutor
    @Mock lateinit var mockWorkQueue: WorkQueue
    @Mock lateinit var mockVersionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockAvdComponents: AvdComponentsBuildService
    @Mock lateinit var mockTestData: StaticTestData
    @Mock lateinit var mockAppApk: File
    @Mock lateinit var mockHelperApk: File
    @Mock lateinit var mockLogger: Logger
    @Mock lateinit var mockUtpConfigFactory: UtpConfigFactory
    @Mock lateinit var mockRetentionConfig: RetentionConfig
    @Mock lateinit var mockCoverageOutputDir: File
    @Mock lateinit var mockAdditionalTestOutputDir: File
    @Mock lateinit var mockDslDevice: ManagedVirtualDevice
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockManagedDeviceShard0: UtpManagedDevice
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockManagedDeviceShard1: UtpManagedDevice
    @Mock lateinit var mockUtpTestResultListenerServerRunner: UtpTestResultListenerServerRunner
    @Mock lateinit var mockUtpTestResultListenerServerMetadata: UtpTestResultListenerServerMetadata
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockUtpDependencies: UtpDependencies
    @Mock lateinit var lockManager: ManagedVirtualDeviceLockManager
    @Mock lateinit var deviceLock: ManagedVirtualDeviceLockManager.DeviceLock
    @Mock private lateinit var emulatorProvider: Provider<Directory>
    @Mock private lateinit var emulatorDirectory: Directory
    @Mock private lateinit var avdProvider: Provider<Directory>
    @Mock private lateinit var avdDirectory: Directory
    private lateinit var emulatorFolder: File
    private lateinit var avdFolder: File
    private lateinit var emulatorFile: File
    private lateinit var outputDirectory: File

    private lateinit var capturedRunnerConfigs: List<UtpRunnerConfig>
    private var utpInvocationCount: Int = 0

    @Before
    fun setupMocks() {
        Environment.initialize()

        `when`(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        `when`(mockTestData.testedApkFinder).thenReturn { listOf(mockAppApk) }
        `when`(mockUtpConfigFactory.createRunnerConfigProtoForManagedDevice(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                nullable(Int::class.java),
                nullable(ShardConfig::class.java))).then {
            RunnerConfigProto.RunnerConfig.getDefaultInstance()
        }
        `when`(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())

        `when`(mockAvdComponents.lockManager).thenReturn(lockManager)
        `when`(lockManager.lock(any())).thenReturn(deviceLock)

        emulatorFolder = temporaryFolderRule.newFolder("emulator")
        `when`(emulatorDirectory.asFile).thenReturn(emulatorFolder)
        `when`(emulatorProvider.get()).thenReturn(emulatorDirectory)
        `when`(emulatorProvider.isPresent()).thenReturn(true)
        `when`(mockAvdComponents.emulatorDirectory).thenReturn(emulatorProvider)

        avdFolder = temporaryFolderRule.newFolder("avd")
        `when`(avdDirectory.asFile).thenReturn(avdFolder)
        `when`(avdProvider.get()).thenReturn(avdDirectory)
        `when`(mockAvdComponents.avdFolder).thenReturn(avdProvider)

        `when`(mockDslDevice.getName()).thenReturn("testDevice")
        `when`(mockDslDevice.device).thenReturn("Pixel 2")
        `when`(mockDslDevice.apiLevel).thenReturn(28)
        `when`(mockDslDevice.systemImageSource).thenReturn("aosp")
        `when`(mockDslDevice.require64Bit).thenReturn(true)
    }

    private fun <T> runInLinuxEnvironment(function: () -> T): T {
        return try {
            // Need to use a custom set up environment to ensure deterministic behavior.
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                // This will ensure the config believes we are running on an x86_64 Linux machine.
                // This will guarantee the x86 system-image is selected.
                systemPropertyOverrides.setProperty("os.name", "Linux")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86_64")

                function.invoke()
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }

    private fun runUtp(
        result: Boolean,
        numShards: Int? = null,
        hasEmulatorTimeoutException: List<Boolean> = List(numShards ?: 1) { false },
    ): Boolean {

        return runInLinuxEnvironment {
            val runner = ManagedDeviceTestRunner(
                mockWorkerExecutor,
                mockUtpDependencies,
                mockVersionedSdkLoader,
                mockRetentionConfig,
                useOrchestrator = false,
                numShards,
                "auto-no-window",
                showEmulatorKernelLogging = false,
                mockAvdComponents,
                null,
                false,
                Level.WARNING,
                mockUtpConfigFactory
            ) { runnerConfigs, _, _, resultsDir, _ ->
                utpInvocationCount++
                capturedRunnerConfigs = runnerConfigs
                TestSuiteResult.getDefaultInstance()
                    .writeTo(File(resultsDir, TEST_RESULT_PB_FILE_NAME).outputStream())
                runnerConfigs.map {
                    UtpTestRunResult(
                        result,
                        createTestSuiteResult(
                            hasEmulatorTimeoutException[it.shardConfig?.index ?: 0]
                        )
                    )
                }
            }

            outputDirectory = temporaryFolderRule.newFolder("results")
            runner.runTests(
                mockDslDevice,
                "mockDeviceId",
                outputDirectory,
                mockCoverageOutputDir,
                mockAdditionalTestOutputDir,
                "projectPath",
                "variantName",
                mockTestData,
                listOf(),
                setOf(mockHelperApk),
                mockLogger
            )
        }
    }

    private fun createTestSuiteResult(
        hasEmulatorTimeoutException: Boolean = false
    ): TestSuiteResult {
        return TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
            if (hasEmulatorTimeoutException) {
                platformErrorBuilder.apply {
                    addErrorsBuilder().apply {
                        causeBuilder.apply {
                            summaryBuilder.apply {
                                stackTrace = "EmulatorTimeoutException"
                            }
                        }
                    }
                }
            }
        }.build()
    }

    @Test
    fun runUtpAndPassed() {
        val result = runUtp(result = true)

        assertThat(utpInvocationCount).isEqualTo(1)
        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isTrue()
        assertThat(File(outputDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun runUtpAndFailed() {
        val result = runUtp(result = false)

        assertThat(utpInvocationCount).isEqualTo(1)
        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isFalse()
    }

    @Test
    fun runUtpWithShardsAndPassed() {
        `when`(deviceLock.lockCount).thenReturn(2)
        val result = runUtp(result = true, numShards = 2)

        assertThat(capturedRunnerConfigs).hasSize(2)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp1")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())
        assertThat(capturedRunnerConfigs[0].shardConfig).isEqualTo(ShardConfig(2, 0))
        assertThat(capturedRunnerConfigs[1].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp2")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())
        assertThat(capturedRunnerConfigs[1].shardConfig).isEqualTo(ShardConfig(2, 1))

        assertThat(result).isTrue()
        assertThat(File(outputDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }

    @Test
    fun rerunUtpWhenEmulatorTimeoutExceptionOccurs() {
        `when`(deviceLock.lockCount).thenReturn(2)
        val result = runUtp(
            result = true,
            numShards = 2,
            hasEmulatorTimeoutException = listOf(true, false))

        assertThat(utpInvocationCount).isEqualTo(2)
        assertThat(result).isTrue()
        assertThat(File(outputDirectory, TEST_RESULT_PB_FILE_NAME)).exists()
    }
}
