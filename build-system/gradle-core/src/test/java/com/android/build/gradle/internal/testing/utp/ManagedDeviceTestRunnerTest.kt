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
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.testutils.MockitoKt.any
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
import java.io.File
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * Unit tests for [ManagedDeviceTestRunner].
 */
class ManagedDeviceTestRunnerTest {
    @get:Rule var mockitoJUnitRule: MockitoRule =
            MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    @get:Rule var temporaryFolderRule = TemporaryFolder()

    @Mock lateinit var mockWorkerExecutor: WorkerExecutor
    @Mock lateinit var mockWorkQueue: WorkQueue
    @Mock lateinit var mockVersionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    @Mock lateinit var mockTestData: StaticTestData
    @Mock lateinit var mockAppApk: File
    @Mock lateinit var mockHelperApk: File
    @Mock lateinit var mockLogger: ILogger
    @Mock lateinit var mockUtpConfigFactory: UtpConfigFactory
    @Mock lateinit var mockRetentionConfig: RetentionConfig
    @Mock lateinit var mockCoverageOutputDir: File
    @Mock lateinit var mockManagedDevice: UtpManagedDevice
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockManagedDeviceShard0: UtpManagedDevice
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockManagedDeviceShard1: UtpManagedDevice
    @Mock lateinit var mockUtpTestResultListenerServerRunner: UtpTestResultListenerServerRunner
    @Mock lateinit var mockUtpTestResultListenerServerMetadata: UtpTestResultListenerServerMetadata
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockUtpDependencies: UtpDependencies

    private lateinit var capturedRunnerConfigs: List<UtpRunnerConfig>

    @Before
    fun setupMocks() {
        `when`(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        `when`(mockTestData.testedApkFinder).thenReturn { _, _ -> listOf(mockAppApk) }
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
                nullable(ShardConfig::class.java))).then {
            RunnerConfigProto.RunnerConfig.getDefaultInstance()
        }
        `when`(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())
    }

    private fun setupFullManagedDevice() {
        `when`(mockManagedDevice.id).thenReturn("mockDeviceId")
        `when`(mockManagedDevice.deviceName).thenReturn("mockDeviceName")
        `when`(mockManagedDevice.api).thenReturn(28)
    }

    private fun setupManagedDeviceForShards() {
        `when`(mockManagedDeviceShard0.id).thenReturn("mockDeviceId_0")
        `when`(mockManagedDeviceShard1.id).thenReturn("mockDeviceId_1")
        `when`(mockManagedDeviceShard0.deviceName).thenReturn("mockDeviceName")
        `when`(mockManagedDeviceShard1.deviceName).thenReturn("mockDeviceName")
        `when`(mockManagedDevice.api).thenReturn(28)
        `when`(mockManagedDevice.forShard(eq(0))).thenReturn(mockManagedDeviceShard0)
        `when`(mockManagedDevice.forShard(eq(1))).thenReturn(mockManagedDeviceShard1)
    }

    private fun runUtp(result: Boolean, numShards: Int? = null): Boolean {
        val runner = ManagedDeviceTestRunner(
            mockWorkerExecutor,
            mockUtpDependencies,
            mockVersionedSdkLoader,
            mockRetentionConfig,
            useOrchestrator = false,
            numShards,
            mockUtpConfigFactory) { runnerConfigs, _, _, _, _ ->
            capturedRunnerConfigs = runnerConfigs
            runnerConfigs.map { result }.toList()
        }

        return runner.runTests(
            mockManagedDevice,
            temporaryFolderRule.newFolder("results"),
            mockCoverageOutputDir,
            "projectPath",
            "variantName",
            mockTestData,
            listOf(),
            setOf(mockHelperApk),
            mockLogger)
    }

    @Test
    fun runUtpAndPassed() {
        setupFullManagedDevice()
        val result = runUtp(result = true)

        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isTrue()
    }

    @Test
    fun runUtpAndFailed() {
        setupFullManagedDevice()
        val result = runUtp(result = false)

        assertThat(capturedRunnerConfigs).hasSize(1)
        assertThat(capturedRunnerConfigs[0].runnerConfig(
            mockUtpTestResultListenerServerMetadata,
            temporaryFolderRule.newFolder("tmp")))
            .isEqualTo(RunnerConfigProto.RunnerConfig.getDefaultInstance())

        assertThat(result).isFalse()
    }

    @Test
    fun runUtpWithShardsAndPassed() {
        setupManagedDeviceForShards()
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
    }
}
