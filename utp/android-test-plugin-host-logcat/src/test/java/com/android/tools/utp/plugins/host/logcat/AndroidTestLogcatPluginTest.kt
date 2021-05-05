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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.api.config.ConfigBase
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Test
import org.mockito.junit.MockitoJUnit
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.quality.Strictness

/**
 * Unit tests for [AndroidTestLogcatPlugin]
 */
@RunWith(JUnit4::class)
class AndroidTestLogcatPluginTest {
    @get:Rule val mockitoJUnitRule = MockitoJUnit.rule().strictness(Strictness.LENIENT)
    @get:Rule var tempFolder: TemporaryFolder = TemporaryFolder()

    @Mock private lateinit var mockCommandHandle: CommandHandle
    @Mock private lateinit var mockConfig: ConfigBase
    @Mock private lateinit var mockDeviceController: DeviceController

    private lateinit var androidTestLogcatPlugin: AndroidTestLogcatPlugin
    private lateinit var emptyTestResult: TestResult
    private lateinit var emptyTestSuiteResult: TestSuiteResult
    private lateinit var environment: Environment

    private val testDeviceTime = "01-01 00:00:00"
    private val logcatOptions = listOf("shell", "logcat", "-v", "threadtime", "-b", "main")
    private val logcatOutputText = """
        04-28 23:18:49.444  1887  1988 I TestRunner: started: (.)
        04-28 23:18:50.444  1887  1988 I ExampleTestApp: test logcat output
        04-28 23:18:51.444  1887  1988 I TestRunner: finished: (.)
    """.trimIndent()

    @Before
    fun setUp() {
        environment = Environment(tempFolder.root.path, "", "", "", null)
        emptyTestResult = TestResult.newBuilder().build()
        emptyTestSuiteResult = TestSuiteResult.newBuilder().build()
        androidTestLogcatPlugin = AndroidTestLogcatPlugin()

        `when`(mockConfig.environment).thenReturn(environment)
        `when`(mockDeviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S")))
                .thenReturn(CommandResult(0, listOf(testDeviceTime)))
        `when`(mockDeviceController.executeAsync(
                eq(listOf(
                        "shell", "logcat",
                        "-v", "threadtime",
                        "-b", "main",
                        "-T", "\'$testDeviceTime.000\'")),
                any())).then  {
            val outputTextProcessor: (String) -> Unit = it.getArgument(1)
            logcatOutputText.lines().forEach(outputTextProcessor)
            mockCommandHandle
        }
    }

    @Test
    fun beforeAll_startsLogcatStreamWithExpectedLogcatOptions() {
        androidTestLogcatPlugin.configure(mockConfig)
        androidTestLogcatPlugin.beforeAll(mockDeviceController)

        val expectedLogcatOptions = mutableListOf<String>()
        expectedLogcatOptions.addAll(logcatOptions)
        expectedLogcatOptions.addAll(listOf("-T", "\'$testDeviceTime.000\'"))

        verify(mockDeviceController).executeAsync(eq(expectedLogcatOptions), any())
    }

    @Test
    fun afterEach_addsLogcatArtifacts() {
        val testResult = androidTestLogcatPlugin.run {
            configure(mockConfig)
            beforeAll(mockDeviceController)
            beforeEach(emptyTestResult.testCase, mockDeviceController)
            afterEach(emptyTestResult, mockDeviceController)
        }

        assertThat(testResult.outputArtifactList).isNotEmpty()
        testResult.outputArtifactList.forEach {
            assertThat(it.label.namespace).isEqualTo("android")
            assertThat(it.label.label).isEqualTo("logcat")
            assertThat(it.sourcePath.path).endsWith("logcat-.-.txt")
        }
    }

    @Test
    fun afterAll_stopsLogcatStream() {
        androidTestLogcatPlugin.configure(mockConfig)
        androidTestLogcatPlugin.beforeAll(mockDeviceController)
        androidTestLogcatPlugin.afterEach(emptyTestResult, mockDeviceController)
        androidTestLogcatPlugin.afterAll(emptyTestSuiteResult, mockDeviceController)

        verify(mockCommandHandle).stop()
    }

    @Test
    fun canRun_isTrue() {
        assertThat(androidTestLogcatPlugin.canRun()).isTrue()
    }
}
