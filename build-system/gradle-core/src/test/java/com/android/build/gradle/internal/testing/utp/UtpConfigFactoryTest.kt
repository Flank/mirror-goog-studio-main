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
import com.android.build.gradle.internal.SdkComponentsBuildService.VersionedSdkLoader
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import java.io.File
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [UtpConfigFactory].
 */
class UtpConfigFactoryTest {
    @get:Rule var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var versionedSdkLoader: VersionedSdkLoader
    @Mock private lateinit var mockAppApk: File
    @Mock private lateinit var mockTestApk: File
    @Mock private lateinit var mockHelperApk: File
    @Mock private lateinit var mockDevice: DeviceConnector
    @Mock private lateinit var mockOutputDir: File
    @Mock private lateinit var mockCoverageOutputDir: File
    @Mock private lateinit var mockTmpDir: File
    @Mock private lateinit var mockSdkDir: File
    @Mock private lateinit var mockAdb: RegularFile
    @Mock private lateinit var mockAdbFile: File
    @Mock private lateinit var mockAdbProvider: Provider<RegularFile>
    @Mock private lateinit var mockBuildToolInfo: BuildToolInfo
    @Mock private lateinit var mockBuildToolInfoProvider: Provider<BuildToolInfo>
    @Mock private lateinit var mockRetentionConfig: RetentionConfig
    @Mock private lateinit var mockResultListenerClientCert: File
    @Mock private lateinit var mockResultListenerClientPrivateKey: File
    @Mock private lateinit var mockTrustCertCollection: File

    private lateinit var testResultListenerServerMetadata: UtpTestResultListenerServerMetadata

    private val testData = StaticTestData(
        testedApplicationId = "com.example.application",
        applicationId = "com.example.application.test",
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner",
        instrumentationRunnerArguments = emptyMap(),
        animationsDisabled = false,
        isTestCoverageEnabled = false,
        minSdkVersion = AndroidVersionImpl(1),
        isLibrary = false,
        flavorName = "",
        testApk = mockFile("testApk.apk"),
        testDirectories = emptyList(),
        testedApkFinder = { _, _ -> emptyList() }
    )

    private val utpDependencies: UtpDependencies = mock(UtpDependencies::class.java) {
        FakeConfigurableFileCollection(
            mockFile("path-to-${it.method.name.removePrefix("get")}.jar"))
    }

    private fun mockFile(absolutePath: String): File = mock(File::class.java).also {
        `when`(it.absolutePath).thenReturn(absolutePath)
    }

    @Before
    fun setupMocks() {
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockDevice.name).thenReturn("mockDeviceName")
        `when`(mockOutputDir.absolutePath).thenReturn("mockOutputDirPath")
        `when`(mockCoverageOutputDir.absolutePath).thenReturn("mockCoverageOutputDir")
        `when`(mockTmpDir.absolutePath).thenReturn("mockTmpDirPath")
        `when`(mockAppApk.absolutePath).thenReturn("mockAppApkPath")
        `when`(mockTestApk.absolutePath).thenReturn("mockTestApkPath")
        `when`(mockHelperApk.absolutePath).thenReturn("mockHelperApkPath")
        `when`(versionedSdkLoader.sdkDirectoryProvider).thenReturn(
            FakeGradleProvider(
                FakeGradleDirectory(mockSdkDir)
            )
        )
        `when`(mockSdkDir.absolutePath).thenReturn("mockSdkDirPath")
        `when`(versionedSdkLoader.adbExecutableProvider).thenReturn(mockAdbProvider)
        `when`(mockAdbProvider.get()).thenReturn(mockAdb)
        `when`(mockAdb.asFile).thenReturn(mockAdbFile)
        `when`(mockAdbFile.absolutePath).thenReturn("mockAdbPath")
        `when`(versionedSdkLoader.buildToolInfoProvider).thenReturn(mockBuildToolInfoProvider)
        `when`(mockBuildToolInfoProvider.get()).thenReturn(mockBuildToolInfo)
        `when`(mockBuildToolInfo.getPath(ArgumentMatchers.any())).then {
            when (it.getArgument<BuildToolInfo.PathId>(0)) {
                BuildToolInfo.PathId.AAPT -> "mockAaptPath"
                BuildToolInfo.PathId.DEXDUMP -> "mockDexdumpPath"
                else -> null
            }
        }
        `when`(mockResultListenerClientCert.absolutePath).thenReturn("mockResultListenerClientCertPath")
        `when`(mockResultListenerClientPrivateKey.absolutePath).thenReturn("mockResultListenerClientPrivateKeyPath")
        `when`(mockTrustCertCollection.absolutePath).thenReturn("mockTrustCertCollectionPath")
        testResultListenerServerMetadata = UtpTestResultListenerServerMetadata(
                serverCert = mockTrustCertCollection,
                serverPort = 1234,
                clientCert = mockResultListenerClientCert,
                clientPrivateKey = mockResultListenerClientPrivateKey
        )
    }

    private fun createForLocalDevice(
            testData: StaticTestData = this.testData,
            useOrchestrator: Boolean = false
    ): RunnerConfigProto.RunnerConfig {
        return UtpConfigFactory().createRunnerConfigProtoForLocalDevice(
                mockDevice,
                testData,
                listOf(mockAppApk, mockTestApk),
                listOf("-additional_install_option"),
                listOf(mockHelperApk),
                utpDependencies,
                versionedSdkLoader,
                mockOutputDir,
                mockTmpDir,
                mockRetentionConfig,
                mockCoverageOutputDir,
                useOrchestrator,
                1234,
                mockResultListenerClientCert,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection,
        )
    }

    private fun createForManagedDevice(
            useOrchestrator: Boolean = false
    ): RunnerConfigProto.RunnerConfig {
        val managedDevice = UtpManagedDevice(
                "deviceName",
                "avdName",
                29,
                "x86",
                "path/to/gradle/avd",
                ":app:deviceNameDebugAndroidTest",
                "path/to/emulator",
                false)
        return UtpConfigFactory().createRunnerConfigProtoForManagedDevice(
                managedDevice,
                testData,
                listOf(mockAppApk, mockTestApk),
                listOf("-additional_install_option"),
                listOf(mockHelperApk),
                utpDependencies,
                versionedSdkLoader,
                mockOutputDir,
                mockTmpDir,
                mockRetentionConfig,
                useOrchestrator,
                testResultListenerServerMetadata
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDevice() {
        val runnerConfigProto = createForLocalDevice()
        assertRunnerConfigProto(runnerConfigProto)
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceUseOrchestrator() {
        val runnerConfigProto = createForLocalDevice(useOrchestrator = true)
        assertRunnerConfigProto(runnerConfigProto, useOrchestrator = true)
    }

    @Test
    fun createRunnerConfigProtoWithIcebox() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForLocalDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                emulator_grpc_port: 8554
                setup_strategy: CONNECT_BEFORE_ALL_TEST
            """)
    }

    @Test
    fun createRunnerConfigProtoWithDebugAndIcebox() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(instrumentationRunnerArguments = mapOf("debug" to "true")))

        assertRunnerConfigProto(runnerConfigProto, instrumentationArgs = mapOf("debug" to "true"))
    }

    @Test
    fun createRunnerConfigProtoWithIceboxAndCompression() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.maxSnapshots).thenReturn(2)
        `when`(mockRetentionConfig.retainAll).thenReturn(false)
        `when`(mockRetentionConfig.compressSnapshots).thenReturn(true)

        val runnerConfigProto = createForLocalDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                emulator_grpc_port: 8554
                max_snapshot_number: 2
                snapshot_compression: TARGZ
                setup_strategy: CONNECT_BEFORE_ALL_TEST
            """)
    }

    @Test
    fun createRunnerConfigProtoWithIceboxAndOrchestrator() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForLocalDevice(useOrchestrator = true)

        assertRunnerConfigProto(
            runnerConfigProto,
            useOrchestrator = true,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                emulator_grpc_port: 8554
                setup_strategy: RECONNECT_BETWEEN_TEST_CASES
            """)
    }

    @Test
    fun createRunnerConfigProtoForManagedDevice() {
        val runnerConfigProto = createForManagedDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true
        )
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceUseOrchestrator() {
        val runnerConfigProto = createForManagedDevice(useOrchestrator = true)

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useOrchestrator = true,
            useGradleManagedDeviceProvider = true
        )
    }

    @Test
    fun createRunnerConfigManagedDeviceWithRetention() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val runnerConfigProto = createForManagedDevice()

        assertRunnerConfigProto(
            runnerConfigProto,
            deviceId = ":app:deviceNameDebugAndroidTest",
            useGradleManagedDeviceProvider = true,
            instrumentationArgs = mapOf("debug" to "true"),
            iceboxConfig = """
                app_package: "com.example.application"
                emulator_grpc_address: "localhost"
                setup_strategy: CONNECT_BEFORE_ALL_TEST
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithTestCoverage() {
        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(isTestCoverageEnabled = true)
        )

        assertRunnerConfigProto(
            runnerConfigProto,
            instrumentationArgs = mapOf(
                "coverage" to "true",
                "coverageFile" to "/data/data/com.example.application/coverage.ec",
            ),
            testCoverageConfig = """
                single_coverage_file: "/data/data/com.example.application/coverage.ec"
                run_as_package_name: "com.example.application"
                output_directory_on_host: "mockCoverageOutputDir/mockDeviceName/"
            """
        )
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceWithTestCoverageAndOrchestrator() {
        val runnerConfigProto = createForLocalDevice(
            testData = testData.copy(isTestCoverageEnabled = true),
            useOrchestrator = true
        )

        assertRunnerConfigProto(
            runnerConfigProto,
            useOrchestrator = true,
            instrumentationArgs = mapOf(
                "coverage" to "true",
                "coverageFilePath" to "/data/data/com.example.application/coverage_data/",
            ),
            testCoverageConfig = """
                multiple_coverage_files_in_directory: "/data/data/com.example.application/coverage_data/"
                run_as_package_name: "com.example.application"
                output_directory_on_host: "mockCoverageOutputDir/mockDeviceName/"
            """
        )
    }

    @Test
    fun createServerConfigProto() {
        val factory = UtpConfigFactory()
        val serverConfigProto = factory.createServerConfigProto()

        assertThat(serverConfigProto.toString().trim()).isEqualTo("""
            address: "localhost:20000"
        """.trimIndent())
    }
}
