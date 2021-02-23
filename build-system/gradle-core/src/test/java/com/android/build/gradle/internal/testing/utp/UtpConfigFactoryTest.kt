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
import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
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
import java.io.File

/**
 * Unit tests for [UtpConfigFactory].
 */
class UtpConfigFactoryTest {
    @get:Rule var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()
    @Mock lateinit var versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader
    @Mock
    lateinit var mockAppApk: File
    @Mock
    lateinit var mockTestApk: File
    @Mock
    lateinit var mockHelperApk: File
    @Mock
    lateinit var mockDevice: DeviceConnector
    @Mock
    lateinit var mockOutputDir: File
    @Mock
    lateinit var mockTmpDir: File
    @Mock
    lateinit var mockSdkDir: File
    @Mock
    lateinit var mockAdb: RegularFile
    @Mock
    lateinit var mockAdbFile: File
    @Mock
    lateinit var mockAdbProvider: Provider<RegularFile>
    @Mock
    lateinit var mockBuildToolInfo: BuildToolInfo
    @Mock
    lateinit var mockBuildToolInfoProvider: Provider<BuildToolInfo>
    @Mock
    lateinit var mockRetentionConfig: RetentionConfig
    @Mock
    lateinit var mockResultListenerClientCert: File
    @Mock
    lateinit var mockResultListenerClientPrivateKey: File
    @Mock
    lateinit var mockTrustCertCollection: File
    val testData = StaticTestData(
        testedApplicationId = "com.example.application",
        applicationId = "com.example.application.test",
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner",
        instrumentationRunnerArguments = emptyMap<String, String>(),
        animationsDisabled = false,
        isTestCoverageEnabled = false,
        minSdkVersion = AndroidVersionImpl(1),
        isLibrary = false,
        flavorName = "",
        testApk = File(""),
        testDirectories = emptyList(),
        testedApkFinder = { _, _ -> emptyList() }
    )
    private val utpDependencies = object: UtpDependencies() {
        private fun mockFile(absolutePath: String): File = mock(File::class.java).also {
            `when`(it.absolutePath).thenReturn(absolutePath)
        }

        override val launcher = FakeConfigurableFileCollection(File(""))
        override val core = FakeConfigurableFileCollection(File(""))
        override val deviceControllerDdmlib = FakeConfigurableFileCollection(mockFile("pathToANDROID_DEVICE_CONTROLLER_DDMLIB.jar"))
        override val deviceProviderGradle = FakeConfigurableFileCollection(mockFile("pathToANDROID_DEVICE_PROVIDER_GRADLE.jar"))
        override val deviceProviderVirtual = FakeConfigurableFileCollection(mockFile("pathToANDROID_DEVICE_PROVIDER_VIRTUAL.jar"))
        override val driverInstrumentation = FakeConfigurableFileCollection(mockFile("pathToANDROID_DRIVER_INSTRUMENTATION.jar"))
        override val testPlugin = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_PLUGIN.jar"))
        override val testDeviceInfoPlugin = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"))
        override val testPluginHostRetention = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_PLUGIN_HOST_RETENTION.jar"))
        override val testPluginResultListenerGradle = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.jar"))
    }

    @Before
    fun setupMocks() {
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockOutputDir.absolutePath).thenReturn("mockOutputDirPath")
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
    }

    @Test
    fun createRunnerConfigProtoForLocalDevice() {
        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProtoForLocalDevice(
            mockDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            versionedSdkLoader,
            mockOutputDir,
            mockTmpDir,
            mockRetentionConfig,
            useOrchestrator = false,
            testResultListenerServerPort = 1234,
            mockResultListenerClientCert,
            mockResultListenerClientPrivateKey,
            mockTrustCertCollection)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_DDMLIB"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_CONTROLLER_DDMLIB.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.LocalAndroidDeviceProvider"
                  value: "\022\026mockDeviceSerialNumber"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000\020\200\347\204\017"
                }
              }
            }
            test_result_listener {
              label {
                label: "ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE"
              }
              class_name: "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener"
              jar {
                path: "pathToANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.jar"
              }
              config {
                type_url: "type.googleapis.com/com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfig"
                value: "\b\322\t\022 mockResultListenerClientCertPath\032&mockResultListenerClientPrivateKeyPath\"\033mockTrustCertCollectionPath"
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: "mockDeviceSerialNumber"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

            """.trimIndent())
    }

    @Test
    fun createRunnerConfigProtoForLocalDeviceUseOrchestrator() {
        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProtoForLocalDevice(
                mockDevice,
                testData,
                listOf(mockAppApk, mockTestApk, mockHelperApk),
                utpDependencies,
                versionedSdkLoader,
                mockOutputDir,
                mockTmpDir,
                mockRetentionConfig,
                useOrchestrator = true,
                testResultListenerServerPort = 1234,
                mockResultListenerClientCert,
                mockResultListenerClientPrivateKey,
                mockTrustCertCollection)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_DDMLIB"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_CONTROLLER_DDMLIB.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.LocalAndroidDeviceProvider"
                  value: "\022\026mockDeviceSerialNumber"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000\020\200\347\204\017\030\001"
                }
              }
            }
            test_result_listener {
              label {
                label: "ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE"
              }
              class_name: "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener"
              jar {
                path: "pathToANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.jar"
              }
              config {
                type_url: "type.googleapis.com/com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfig"
                value: "\b\322\t\022 mockResultListenerClientCertPath\032&mockResultListenerClientPrivateKeyPath\"\033mockTrustCertCollectionPath"
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: "mockDeviceSerialNumber"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

            """.trimIndent())
    }

    @Test
    fun createRunnerConfigProtoWithIcebox() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProtoForLocalDevice(
            mockDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            versionedSdkLoader,
            mockOutputDir,
            mockTmpDir,
            mockRetentionConfig,
            useOrchestrator = false,
            testResultListenerServerPort = 1234,
            mockResultListenerClientCert,
            mockResultListenerClientPrivateKey,
            mockTrustCertCollection)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_DDMLIB"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_CONTROLLER_DDMLIB.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.LocalAndroidDeviceProvider"
                  value: "\022\026mockDeviceSerialNumber"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN_HOST_RETENTION"
                }
                class_name: "com.android.tools.utp.plugins.host.icebox.IceboxPlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN_HOST_RETENTION.jar"
                }
                config {
                  type_url: "type.googleapis.com/com.android.tools.utp.plugins.host.icebox.proto.IceboxPlugin"
                  value: "\n\027com.example.application\022\tlocalhost\030\352B"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\ns\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\017\022\r\n\005debug\022\004true\020\200\347\204\017"
                }
              }
            }
            test_result_listener {
              label {
                label: "ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE"
              }
              class_name: "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener"
              jar {
                path: "pathToANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.jar"
              }
              config {
                type_url: "type.googleapis.com/com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfig"
                value: "\b\322\t\022 mockResultListenerClientCertPath\032&mockResultListenerClientPrivateKeyPath\"\033mockTrustCertCollectionPath"
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: "mockDeviceSerialNumber"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

            """.trimIndent())
    }

    @Test
    fun createRunnerConfigProtoWithIceboxAndCompression() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.maxSnapshots).thenReturn(2)
        `when`(mockRetentionConfig.retainAll).thenReturn(false)
        `when`(mockRetentionConfig.compressSnapshots).thenReturn(true)

        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProtoForLocalDevice(
            mockDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            versionedSdkLoader,
            mockOutputDir,
            mockTmpDir,
            mockRetentionConfig,
            useOrchestrator = false,
            testResultListenerServerPort = 1234,
            mockResultListenerClientCert,
            mockResultListenerClientPrivateKey,
            mockTrustCertCollection)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_DDMLIB"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_CONTROLLER_DDMLIB.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.LocalAndroidDeviceProvider"
                  value: "\022\026mockDeviceSerialNumber"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN_HOST_RETENTION"
                }
                class_name: "com.android.tools.utp.plugins.host.icebox.IceboxPlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN_HOST_RETENTION.jar"
                }
                config {
                  type_url: "type.googleapis.com/com.android.tools.utp.plugins.host.icebox.proto.IceboxPlugin"
                  value: "\n\027com.example.application\022\tlocalhost\030\352B(\0028\001"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\ns\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\017\022\r\n\005debug\022\004true\020\200\347\204\017"
                }
              }
            }
            test_result_listener {
              label {
                label: "ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE"
              }
              class_name: "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener"
              jar {
                path: "pathToANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.jar"
              }
              config {
                type_url: "type.googleapis.com/com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfig"
                value: "\b\322\t\022 mockResultListenerClientCertPath\032&mockResultListenerClientPrivateKeyPath\"\033mockTrustCertCollectionPath"
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: "mockDeviceSerialNumber"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

            """.trimIndent())
    }

    @Test
    fun createRunnerConfigProtoWithIceboxAndOrchestrator() {
        `when`(mockRetentionConfig.enabled).thenReturn(true)
        `when`(mockRetentionConfig.retainAll).thenReturn(true)

        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProtoForLocalDevice(
            mockDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            versionedSdkLoader,
            mockOutputDir,
            mockTmpDir,
            mockRetentionConfig,
            useOrchestrator = true,
            testResultListenerServerPort = 1234,
            mockResultListenerClientCert,
            mockResultListenerClientPrivateKey,
            mockTrustCertCollection)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_DDMLIB"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_CONTROLLER_DDMLIB.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.LocalAndroidDeviceProvider"
                  value: "\022\026mockDeviceSerialNumber"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000\020\200\347\204\017\030\001"
                }
              }
            }
            test_result_listener {
              label {
                label: "ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE"
              }
              class_name: "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener"
              jar {
                path: "pathToANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE.jar"
              }
              config {
                type_url: "type.googleapis.com/com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfig"
                value: "\b\322\t\022 mockResultListenerClientCertPath\032&mockResultListenerClientPrivateKeyPath\"\033mockTrustCertCollectionPath"
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: "mockDeviceSerialNumber"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

            """.trimIndent())
    }

    @Test
    fun createRunnerConfigProtoForManagedDevice() {
        val factory = UtpConfigFactory()
        val managedDevice = UtpManagedDevice(
            "deviceName",
            "avdName",
            29,
            "x86",
            "path/to/gradle/avd",
            ":app:deviceNameDebugAndroidTest",
            "path/to/emulator",
            false)
        val runnerConfigProto = factory.createRunnerConfigProtoForManagedDevice(
            managedDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            versionedSdkLoader,
            mockOutputDir,
            mockTmpDir,
            mockRetentionConfig,
            useOrchestrator = false
        )
        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: ":app:deviceNameDebugAndroidTest"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_GRADLE"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.gradle.GradleManagedAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_PROVIDER_GRADLE.jar"
                }
                config {
                  type_url: "type.googleapis.com/com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderConfig"
                  value: "\n\346\001\nW\n?type.googleapis.com/google.testing.platform.proto.api.core.Path\022\024\n\022path/to/gradle/avd\022\aavdName\032\037:app:deviceNameDebugAndroidTest*U\n?type.googleapis.com/google.testing.platform.proto.api.core.Path\022\022\n\020path/to/emulator2\ndeviceName\020\255\'"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000\020\200\347\204\017"
                }
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: ":app:deviceNameDebugAndroidTest"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

        """.trimIndent())
    }

    @Test
    fun createRunnerConfigProtoForManagedDeviceUseOrchestrator() {
        val factory = UtpConfigFactory()
        val managedDevice = UtpManagedDevice(
                "deviceName",
                "avdName",
                29,
                "x86",
                "path/to/gradle/avd",
                ":app:deviceNameDebugAndroidTest",
                "path/to/emulator",
                false)
        val runnerConfigProto = factory.createRunnerConfigProtoForManagedDevice(
                managedDevice,
                testData,
                listOf(mockAppApk, mockTestApk, mockHelperApk),
                utpDependencies,
                versionedSdkLoader,
                mockOutputDir,
                mockTmpDir,
                mockRetentionConfig,
                useOrchestrator = true
        )
        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: ":app:deviceNameDebugAndroidTest"
              }
              provider {
                label {
                  label: "ANDROID_DEVICE_PROVIDER_GRADLE"
                }
                class_name: "com.android.tools.utp.plugins.deviceprovider.gradle.GradleManagedAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_PROVIDER_GRADLE.jar"
                }
                config {
                  type_url: "type.googleapis.com/com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderConfig"
                  value: "\n\346\001\nW\n?type.googleapis.com/google.testing.platform.proto.api.core.Path\022\024\n\022path/to/gradle/avd\022\aavdName\032\037:app:deviceNameDebugAndroidTest*U\n?type.googleapis.com/google.testing.platform.proto.api.core.Path\022\022\n\020path/to/emulator2\ndeviceName\020\255\'"
                }
              }
            }
            test_fixture {
              test_fixture_id {
                id: "AGP_Test_Fixture"
              }
              setup {
                installable {
                  source_path {
                    path: "mockAppApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockTestApkPath"
                  }
                  type: ANDROID_APK
                }
                installable {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_PLUGIN"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
                }
                class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
                jar {
                  path: "pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"
                }
              }
              environment {
                output_dir {
                  path: "mockOutputDirPath"
                }
                tmp_dir {
                  path: "mockTmpDirPath"
                }
                android_environment {
                  android_sdk {
                    sdk_path {
                      path: "mockSdkDirPath"
                    }
                    aapt_path {
                      path: "mockAaptPath"
                    }
                    adb_path {
                      path: "mockAdbPath"
                    }
                    dexdump_path {
                      path: "mockDexdumpPath"
                    }
                  }
                  test_log_dir {
                    path: "testlog"
                  }
                  test_run_log {
                    path: "test-results.log"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.testing.platform.runtime.android.driver.AndroidInstrumentationDriver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000\020\200\347\204\017\030\001"
                }
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: ":app:deviceNameDebugAndroidTest"
                }
                test_fixture_id {
                  id: "AGP_Test_Fixture"
                }
              }
            }

        """.trimIndent())
    }

    @Test
    fun createServerConfigProto() {
        val factory = UtpConfigFactory()
        val serverConfigProto = factory.createServerConfigProto()

        assertThat(TextFormat.printToString(serverConfigProto)).isEqualTo("""
            address: "localhost:20000"

        """.trimIndent())
    }
}
