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
import com.android.ide.common.process.ProcessResult
import com.android.testutils.MockitoKt.any
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.utils.ILogger
import com.google.protobuf.Any
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.service.ServerConfigProto.ServerConfig
import java.io.File
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
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
    @Mock lateinit var mockManagedDevice: UtpManagedDevice
    @Mock lateinit var mockUtpTestResultListenerServerRunner: UtpTestResultListenerServerRunner
    @Mock lateinit var mockUtpTestResultListenerServerMetadata: UtpTestResultListenerServerMetadata
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockUtpDependencies: UtpDependencies

    private lateinit var runner: ManagedDeviceTestRunner
    private lateinit var capturedTestResultListener: UtpTestResultListener

    @Before
    fun setupMocks() {
        `when`(mockManagedDevice.deviceName).thenReturn("mockDeviceName")
        `when`(mockManagedDevice.api).thenReturn(28)
        `when`(mockTestData.minSdkVersion).thenReturn(AndroidVersionImpl(28))
        `when`(mockTestData.testedApkFinder).thenReturn { _, _ -> listOf(mockAppApk) }
        `when`(mockUtpConfigFactory.createRunnerConfigProtoForManagedDevice(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).then {
            RunnerConfigProto.RunnerConfig.getDefaultInstance()
        }
        `when`(mockUtpConfigFactory.createServerConfigProto())
                .thenReturn(ServerConfig.getDefaultInstance())
        `when`(mockUtpTestResultListenerServerRunner.metadata)
                .thenReturn(mockUtpTestResultListenerServerMetadata)
        `when`(mockWorkerExecutor.noIsolation()).thenReturn(mockWorkQueue)
        `when`(mockWorkQueue.await()).then {
            val testSuiteResult = createStubResultProto()

            capturedTestResultListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
                testSuiteStartedBuilder.apply {
                    deviceId = "mockDeviceSerialNumber"
                    testSuiteMetadata = Any.pack(testSuiteResult.testSuiteMetaData)
                }
            }.build())
            testSuiteResult.testResultList.forEach { testResult ->
                capturedTestResultListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
                    testCaseStartedBuilder.apply {
                        deviceId = "mockDeviceSerialNumber"
                        testCase = Any.pack(testResult.testCase)
                    }
                }.build())
                capturedTestResultListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
                    testCaseFinishedBuilder.apply {
                        deviceId = "mockDeviceSerialNumber"
                        testCaseResult = Any.pack(testResult)
                    }
                }.build())
            }
            capturedTestResultListener.onTestResultEvent(TestResultEvent.newBuilder().apply {
                testSuiteFinishedBuilder.apply {
                    deviceId = "mockDeviceSerialNumber"
                    this.testSuiteResult = Any.pack(testSuiteResult)
                }
            }.build())

            mock(ProcessResult::class.java)
        }

        runner = ManagedDeviceTestRunner(
                mockWorkerExecutor,
                mockUtpDependencies,
                mockVersionedSdkLoader,
                mockRetentionConfig,
                useOrchestrator = false,
                mockUtpConfigFactory) { utpTestResultListener ->
            capturedTestResultListener = utpTestResultListener
            mockUtpTestResultListenerServerRunner
        }
    }

    private fun createStubResultProto(): TestSuiteResultProto.TestSuiteResult {
        return TextFormat.parse("""
            test_suite_meta_data {
              scheduled_test_case_count: 1
            }
            test_result {
              test_case {
                test_class: "ExampleInstrumentedTest"
                test_package: "com.example.application"
                test_method: "useAppContext"
              }
              test_status: PASSED
            }
        """.trimIndent(), TestSuiteResultProto.TestSuiteResult::class.java)
    }

    @Test
    fun runTests() {
        val resultDir = temporaryFolderRule.newFolder("results").toPath()

        runner.runTests(
                mockManagedDevice,
                resultDir.toFile(),
                "projectName",
                "variantName",
                mockTestData,
                listOf(),
                setOf(mockHelperApk),
                mockLogger)

        val variant = resultDir.resolve("TEST-mockDeviceName-projectName-variantName.xml")
        assertThat(variant).exists()
        assertThat(variant).containsAllOf(
                """<testsuite name="com.example.application.ExampleInstrumentedTest" tests="1" failures="0" errors="0" skipped="0"""",
                """<property name="device" value="mockDeviceName" />""",
                """<property name="flavor" value="variantName" />""",
                """<property name="project" value="projectName" />""",
                """<testcase name="useAppContext" classname="com.example.application.ExampleInstrumentedTest""""
        )
    }
}
