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

import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkParameters
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.utils.ILogger
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.service.ServerConfigProto
import java.io.File
import org.gradle.api.Action
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.contains
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.nullable
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for UtpTestUtils.kt.
 */
class UtpTestUtilsTest {
    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var mockUtpDependencies: UtpDependencies
    @Mock
    private lateinit var mockWorkerExecutor: WorkerExecutor
    @Mock
    private lateinit var mockWorkQueue: WorkQueue
    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var mockRunUtpWorkParameters: RunUtpWorkParameters
    @Mock
    private lateinit var mockLogger: ILogger
    @Mock
    private lateinit var mockUtpTestResultListener: UtpTestResultListener
    @Mock(answer = RETURNS_DEEP_STUBS)
    private lateinit var mockUtpTestResultListenerServerRunner: UtpTestResultListenerServerRunner

    lateinit var utpResultDir: File

    @Before
    fun setupMocks() {
        `when`(mockWorkerExecutor.noIsolation()).thenReturn(mockWorkQueue)
    }

    private fun runUtp(
        shardConfig: ShardConfig? = null,
        stubUtpAction: UtpTestResultListener.() -> Unit = { stubTestSuitePassing() }
    ): List<UtpTestRunResult> {
        val utpOutputDir = temporaryFolderRule.newFolder()
        utpResultDir = temporaryFolderRule.newFolder()
        val config = UtpRunnerConfig(
            "deviceName",
            "deviceId",
            utpOutputDir,
            { _, _ -> RunnerConfigProto.RunnerConfig.getDefaultInstance() },
            ServerConfigProto.ServerConfig.getDefaultInstance(),
            shardConfig
        )

        var capturedUtpTestResultListener: UtpTestResultListener? = null
        `when`(mockWorkQueue.submit(eq(RunUtpWorkAction::class.java), any())).then {
            requireNotNull(capturedUtpTestResultListener).stubUtpAction()
        }

        return runUtpTestSuiteAndWait(
            listOf(config),
            mockWorkerExecutor,
            "projectName",
            "variantName",
            utpResultDir,
            mockLogger,
            mockUtpTestResultListener,
            mockUtpDependencies
        ) {
            capturedUtpTestResultListener = it
            mockUtpTestResultListenerServerRunner
        }
    }

    private fun UtpTestResultListener.stubTestSuitePassing() {
        val testSuiteResult = createStubResultProto()
        onTestResultEvent(TestResultEvent.newBuilder().apply {
            testSuiteStartedBuilder.apply {
                deviceId = "deviceId"
                testSuiteMetadata = Any.pack(testSuiteResult.testSuiteMetaData)
            }
        }.build())
        testSuiteResult.testResultList.forEach { testResult ->
            onTestResultEvent(TestResultEvent.newBuilder().apply {
                testCaseStartedBuilder.apply {
                    deviceId = "deviceId"
                    testCase = Any.pack(testResult.testCase)
                }
            }.build())
            onTestResultEvent(TestResultEvent.newBuilder().apply {
                testCaseFinishedBuilder.apply {
                    deviceId = "deviceId"
                    testCaseResult = Any.pack(testResult)
                }
            }.build())
        }
        onTestResultEvent(TestResultEvent.newBuilder().apply {
            testSuiteFinishedBuilder.apply {
                deviceId = "deviceId"
                this.testSuiteResult = Any.pack(testSuiteResult)
            }
        }.build())
    }

    private fun verifyTestListenerIsInvoked() {
        val testSuiteResult = createStubResultProto()
        inOrder(mockUtpTestResultListener).apply {
            verify(mockUtpTestResultListener).onTestResultEvent(eq(TestResultEvent.newBuilder().apply {
                testSuiteStartedBuilder.apply {
                    deviceId = "deviceId"
                    testSuiteMetadata = Any.pack(testSuiteResult.testSuiteMetaData)
                }
            }.build()))
            testSuiteResult.testResultList.forEach { testResult ->
                verify(mockUtpTestResultListener).onTestResultEvent(eq(TestResultEvent.newBuilder().apply {
                    testCaseStartedBuilder.apply {
                        deviceId = "deviceId"
                        testCase = Any.pack(testResult.testCase)
                    }
                }.build()))
                verify(mockUtpTestResultListener).onTestResultEvent(eq(TestResultEvent.newBuilder().apply {
                    testCaseFinishedBuilder.apply {
                        deviceId = "deviceId"
                        testCaseResult = Any.pack(testResult)
                    }
                }.build()))
            }
            verify(mockUtpTestResultListener).onTestResultEvent(eq(TestResultEvent.newBuilder().apply {
                testSuiteFinishedBuilder.apply {
                    deviceId = "deviceId"
                    this.testSuiteResult = Any.pack(testSuiteResult)
                }
            }.build()))
            verifyNoMoreInteractions()
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
    fun runUtpWorkParametersShouldBeConfiguredAsExpected() {
        runUtp()

        lateinit var setRunUtpWorkParametersAction: Action<in RunUtpWorkParameters>
        verify(mockWorkQueue).submit(
            eq(RunUtpWorkAction::class.java),
            argThat {
                setRunUtpWorkParametersAction = it
                true
            })

        setRunUtpWorkParametersAction.execute(mockRunUtpWorkParameters)

        mockRunUtpWorkParameters.run {
            verify(launcherJar).set(mockUtpDependencies.launcher.singleFile)
            verify(coreJar).set(mockUtpDependencies.core.singleFile)
            verify(runnerConfig).set(argThat<File> {
                it.exists()
            })
            verify(serverConfig).set(argThat<File> {
                it.exists()
            })
            verify(loggingProperties).set(argThat<File> {
                it.exists()
            })
        }
    }

    @Test
    fun failedToReceiveUtpResults() {
        val results = runUtp() { /* Do nothing after the work is posted. */ }

        assertThat(results).containsExactly(UtpTestRunResult(false, null))
        verify(mockLogger).error(
            nullable(Throwable::class.java),
            contains("Failed to receive the UTP test results"))
    }

    @Test
    fun runSuccessfully() {
        val results = runUtp()

        assertThat(results).containsExactly(UtpTestRunResult(true, createStubResultProto()))

        verifyTestListenerIsInvoked()

        val resultsXml = utpResultDir.resolve("TEST-deviceName-projectName-variantName.xml")
        assertThat(resultsXml).exists()
        assertThat(resultsXml).containsAllOf(
            """<testsuite name="com.example.application.ExampleInstrumentedTest" tests="1" failures="0" errors="0" skipped="0"""",
            """<property name="device" value="deviceName" />""",
            """<property name="flavor" value="variantName" />""",
            """<property name="project" value="projectName" />""",
            """<testcase name="useAppContext" classname="com.example.application.ExampleInstrumentedTest""""
        )
    }

    @Test
    fun runSuccessfullyWithSharding() {
        val results = runUtp(ShardConfig(totalCount = 2, index = 0))

        assertThat(results).containsExactly(UtpTestRunResult(true, createStubResultProto()))

        verifyTestListenerIsInvoked()

        val resultsXml = utpResultDir.resolve("TEST-deviceName_0-projectName-variantName.xml")
        assertThat(resultsXml).exists()
        assertThat(resultsXml).containsAllOf(
            """<testsuite name="com.example.application.ExampleInstrumentedTest" tests="1" failures="0" errors="0" skipped="0"""",
            """<property name="device" value="deviceName_0" />""",
            """<property name="flavor" value="variantName" />""",
            """<property name="project" value="projectName" />""",
            """<testcase name="useAppContext" classname="com.example.application.ExampleInstrumentedTest""""
        )
    }

    @Test
    fun utpShouldBeEnabledByDefault() {
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(FakeProviderFactory.factory, mapOf()))

        assertThat(shouldEnableUtp(projectOptions, testOptions = null, variantType = null)).isTrue()
    }

    @Test
    fun utpShouldBeDisabledForDynamicFeatureModule() {
        val projectOptions = ProjectOptions(
            ImmutableMap.of(),
            FakeProviderFactory(FakeProviderFactory.factory, mapOf()))
        val variantType = mock<VariantType>()
        `when`(variantType.isDynamicFeature).thenReturn(true)

        assertThat(shouldEnableUtp(projectOptions, testOptions = null, variantType)).isFalse()
    }

    @Test
    fun resultHasEmulatorTimeoutException() {
        val testResult = TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
            platformErrorBuilder.apply {
                errorDetailBuilder.apply {
                    causeBuilder.apply {
                        summaryBuilder.apply {
                            stackTrace = "EmulatorTimeoutException"
                        }
                    }
                }
            }
        }.build()

        assertThat(hasEmulatorTimeoutException(testResult)).isTrue()
    }

    @Test
    fun resultDoesNotHaveEmulatorTimeoutException() {
        val testResult = TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
            platformErrorBuilder.apply {
                errorDetailBuilder.apply {
                    causeBuilder.apply {
                        summaryBuilder.apply {
                            stackTrace = "Exception"
                        }
                    }
                }
            }
        }.build()

        assertThat(hasEmulatorTimeoutException(testResult)).isFalse()
        assertThat(hasEmulatorTimeoutException(null)).isFalse()
    }
}
