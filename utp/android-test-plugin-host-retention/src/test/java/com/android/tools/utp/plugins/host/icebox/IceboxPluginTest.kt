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

package com.android.tools.utp.plugins.host.icebox

import com.android.testutils.MockitoKt.any
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto
import com.android.tools.utp.plugins.host.icebox.proto.IceboxOutputProto.IceboxOutput
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugins.PluginConfigImpl
import com.google.testing.platform.proto.api.config.AndroidSdkProto
import com.google.testing.platform.proto.api.config.EnvironmentProto.Environment
import com.google.testing.platform.proto.api.config.SetupProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.device.AndroidDevice
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks

/**
 * Unit tests for [IceboxPlugin]
 */
@RunWith(JUnit4::class)
class IceboxPluginTest {
    private val appPackage = "dummyAppPackage"

    @get:Rule
    var tempFolder = TemporaryFolder()

    @Mock
    private lateinit var mockDeviceController: DeviceController

    @Mock
    private lateinit var mockIceboxCaller: IceboxCaller

    @Mock
    private lateinit var mockGrpcInfoFinder: GrpcInfoFinder

    private lateinit var iceboxPlugin: IceboxPlugin
    private lateinit var iceboxPluginConfig: IceboxPluginProto.IceboxPlugin
    private lateinit var config: PluginConfigImpl
    private lateinit var testResult: TestResult
    private lateinit var failingTestResult: TestResult
    private lateinit var testSuiteResult: TestSuiteResult
    private lateinit var snapshotFile: File
    private lateinit var snapshotFileCompressed: File
    private var iceboxCallerCreated = false

    private val grpcAddress = "10.0.0.1"
    private val grpcPort = 8554

    private fun buildIceboxPluginConfig(
            skipSnapshot: Boolean,
            maxSnapshotNumber: Int,
            snapshotCompression: IceboxPluginProto.Compression,
            configGrpcPort: Int = grpcPort
    ):
            IceboxPluginProto.IceboxPlugin {
        return IceboxPluginProto.IceboxPlugin.newBuilder().apply {
            appPackage = this@IceboxPluginTest.appPackage
            emulatorGrpcAddress = grpcAddress
            emulatorGrpcPort = configGrpcPort
            this.skipSnapshot = skipSnapshot
            this.maxSnapshotNumber = maxSnapshotNumber
            this.snapshotCompression = snapshotCompression
        }.build()
    }

    private fun buildConfig(iceboxPluginConfig: IceboxPluginProto.IceboxPlugin): PluginConfigImpl {
        return PluginConfigImpl(
                environmentProto = Environment.newBuilder().apply {
                    outputDirBuilder.path = tempFolder.root.path
                }.build(),
                testSetupProto = SetupProto.TestSetup.getDefaultInstance(),
                androidSdkProto = AndroidSdkProto.AndroidSdk.getDefaultInstance(),
                configProto = Any.pack(iceboxPluginConfig)
        )
    }

    @Before
    fun setup() {
        initMocks(this)
        `when`(mockDeviceController.getDevice()).thenReturn(
                AndroidDevice(
                        serial = "emulator-5554",
                        type = Device.DeviceType.VIRTUAL,
                        port = 5555,
                        emulatorPort = 5554,
                        serverPort = 5037,
                        properties = AndroidDeviceProperties()
                )
        )
        `when`(mockGrpcInfoFinder.findInfo(anyString())).thenReturn(DEFAULT_EMULATOR_GRPC_INFO)
        iceboxPluginConfig = buildIceboxPluginConfig(false, 0, IceboxPluginProto.Compression.NONE)
        config = buildConfig(iceboxPluginConfig)
        testResult = TestResult.getDefaultInstance()
        val testClass = testResult.testCase.testClass
        val testMethod = testResult.testCase.testMethod
        failingTestResult = TestResult.newBuilder().setTestStatus(
                TestStatus.FAILED
        ).build()
        testSuiteResult = TestSuiteResult.getDefaultInstance()
        snapshotFile = File(
                tempFolder.root,
                "snapshot-$testClass-$testMethod-failure0.tar"
        )
        snapshotFileCompressed = File(
                tempFolder.root,
                "snapshot-$testClass-$testMethod-failure0.tar.gz"
        )
        iceboxCallerCreated = false
        iceboxPlugin = IceboxPlugin (
            { _, _, _ ->
                iceboxCallerCreated = true
                mockIceboxCaller
            },
            mockGrpcInfoFinder
        )
    }

    @Test
    fun configure_ok() {
        iceboxPlugin.configure(config = config)
        assertThat(iceboxPlugin.outputDir.path).isEqualTo(tempFolder.root.path)
        assertThat(iceboxPlugin.iceboxPluginConfig).isEqualTo(iceboxPluginConfig)
    }

    @Test
    fun beforeAll_ok() {
        assertThat(iceboxCallerCreated).isFalse()

        iceboxPlugin.configure(config = config)
        iceboxPlugin.beforeAll(mockDeviceController)

        assertThat(iceboxCallerCreated).isTrue()
        verify(mockIceboxCaller).runIcebox(
                any(), anyString(), anyString(), anyInt(),
                anyInt()
        )

        verify(mockGrpcInfoFinder, never()).findInfo(anyString())
    }

    @Test
    fun beforeAll_grpcFinderCall() {
        val localIceboxPluginConfig = buildIceboxPluginConfig(
                true,
                2,
                IceboxPluginProto.Compression.NONE,
                0
        )
        val localConfig = buildConfig(localIceboxPluginConfig)

        iceboxPlugin.configure(config = localConfig)
        iceboxPlugin.beforeAll(mockDeviceController)

        verify(mockGrpcInfoFinder).findInfo(anyString())
    }

    @Test
    fun skipSnapshot_ok() {
        val localIceboxPluginConfig = buildIceboxPluginConfig(
                true,
                0,
                IceboxPluginProto.Compression.NONE
        )
        val localConfig = buildConfig(localIceboxPluginConfig)

        iceboxPlugin.configure(config = localConfig)
        iceboxPlugin.beforeAll(mockDeviceController)

        verify(mockIceboxCaller).runIcebox(
                any(), anyString(), anyString(), eq(0),
                anyInt()
        )
    }

    @Test
    fun afterEach_ok() {
        iceboxPlugin.configure(config = config)
        iceboxPlugin.beforeAll(mockDeviceController)
        iceboxPlugin.beforeEach(TestCaseProto.TestCase.getDefaultInstance(), mockDeviceController)
        val newResult = iceboxPlugin.afterEach(testResult, mockDeviceController)
        // No test artifact when the instrumentation test succeeds. But it will
        // generate icebox artifact when the instrumentation test fails. See
        // onFail_stillCreatesArtifact() for the failing case.
        assertThat(newResult.outputArtifactCount).isEqualTo(0)
    }

    @Test
    fun afterAll_ok() {
        iceboxPlugin.configure(config = config)
        iceboxPlugin.beforeAll(mockDeviceController)
        iceboxPlugin.beforeEach(TestCaseProto.TestCase.getDefaultInstance(), mockDeviceController)
        val newResult = iceboxPlugin.afterEach(testResult, mockDeviceController)
        assertThat(newResult.outputArtifactCount).isEqualTo(0)
        iceboxPlugin.afterAll(testSuiteResult, mockDeviceController)
        verify(mockIceboxCaller, times(1)).shutdownGrpc()
    }

    @Test
    fun onFail_createsArtifact() {
        `when`(
                mockIceboxCaller.fetchSnapshot(any(), any(), any())
        ).thenAnswer { invocation ->
            val file = invocation.getArgument(0, File::class.java)
            file.outputStream().use {
                it.write(1)
            }
            Unit
        }
        iceboxPlugin.configure(config = config)
        iceboxPlugin.beforeAll(mockDeviceController)
        iceboxPlugin.beforeEach(TestCaseProto.TestCase.getDefaultInstance(), mockDeviceController)
        val newResult = iceboxPlugin.afterEach(failingTestResult, mockDeviceController)
        assertThat(newResult.outputArtifactCount).isEqualTo(2)
        val infoFilePath = newResult.outputArtifactList.find {
          it.label.label == "icebox.info" && it.label.namespace == "android"
        }?.sourcePath?.path
        assertThat(infoFilePath).isNotNull()
        assertThat(IceboxOutput.parseFrom(File(infoFilePath).inputStream()).appPackage).isEqualTo(appPackage)
        assertThat(snapshotFile.exists()).isTrue()
        iceboxPlugin.afterAll(testSuiteResult, mockDeviceController)
        verify(mockIceboxCaller, times(1)).shutdownGrpc()
    }

    @Test
    fun onFail_createsArtifactCompressed() {
        `when`(
                mockIceboxCaller.fetchSnapshot(any(), any(), any())
        ).thenAnswer { invocation ->
            val file = invocation.getArgument(0, File::class.java)
            file.outputStream().use {
                it.write(1)
            }
            Unit
        }
        val localIceboxPluginConfig = buildIceboxPluginConfig(
                false, 0,
                IceboxPluginProto.Compression.TARGZ
        )
        val localConfig = buildConfig(localIceboxPluginConfig)
        iceboxPlugin.configure(config = localConfig)
        iceboxPlugin.beforeAll(mockDeviceController)
        iceboxPlugin.beforeEach(TestCaseProto.TestCase.getDefaultInstance(), mockDeviceController)
        val newResult = iceboxPlugin.afterEach(failingTestResult, mockDeviceController)
        assertThat(newResult.outputArtifactCount).isEqualTo(2)
        assertThat(snapshotFileCompressed.exists()).isTrue()
        iceboxPlugin.afterAll(testSuiteResult, mockDeviceController)
        verify(mockIceboxCaller, times(1)).shutdownGrpc()
    }
}
