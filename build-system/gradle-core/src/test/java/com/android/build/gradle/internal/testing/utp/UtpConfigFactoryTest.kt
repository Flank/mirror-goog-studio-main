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
    @Mock lateinit var mockSdkComponents: SdkComponentsBuildService
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
        override val deviceProviderLocal = FakeConfigurableFileCollection(mockFile("pathToANDROID_DEVICE_PROVIDER_LOCAL.jar"))
        override val deviceProviderVirtual = FakeConfigurableFileCollection(mockFile("pathToANDROID_DEVICE_PROVIDER_VIRTUAL.jar"))
        override val driverInstrumentation = FakeConfigurableFileCollection(mockFile("pathToANDROID_DRIVER_INSTRUMENTATION.jar"))
        override val testPlugin = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_PLUGIN.jar"))
        override val testDeviceInfoPlugin = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_DEVICE_INFO_PLUGIN.jar"))
        override val testPluginHostRetention = FakeConfigurableFileCollection(mockFile("pathToANDROID_TEST_PLUGIN_HOST_RETENTION.jar"))
    }

    @Before
    fun setupMocks() {
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockOutputDir.absolutePath).thenReturn("mockOutputDirPath")
        `when`(mockTmpDir.absolutePath).thenReturn("mockTmpDirPath")
        `when`(mockAppApk.absolutePath).thenReturn("mockAppApkPath")
        `when`(mockTestApk.absolutePath).thenReturn("mockTestApkPath")
        `when`(mockHelperApk.absolutePath).thenReturn("mockHelperApkPath")
        `when`(mockSdkComponents.sdkDirectoryProvider).thenReturn(
            FakeGradleProvider(
                FakeGradleDirectory(mockSdkDir)
            )
        )
        `when`(mockSdkDir.absolutePath).thenReturn("mockSdkDirPath")
        `when`(mockSdkComponents.adbExecutableProvider).thenReturn(mockAdbProvider)
        `when`(mockAdbProvider.get()).thenReturn(mockAdb)
        `when`(mockAdb.asFile).thenReturn(mockAdbFile)
        `when`(mockAdbFile.absolutePath).thenReturn("mockAdbPath")
        `when`(mockSdkComponents.buildToolInfoProvider).thenReturn(mockBuildToolInfoProvider)
        `when`(mockBuildToolInfoProvider.get()).thenReturn(mockBuildToolInfo)
        `when`(mockBuildToolInfo.getPath(ArgumentMatchers.any())).then {
            when (it.getArgument<BuildToolInfo.PathId>(0)) {
                BuildToolInfo.PathId.AAPT -> "mockAaptPath"
                BuildToolInfo.PathId.DEXDUMP -> "mockDexdumpPath"
                else -> null
            }
        }
    }

    @Test
    fun createRunnerConfigProtoForLocalDevice() {
        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProtoForLocalDevice(
            mockDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            mockSdkComponents,
            mockOutputDir,
            mockTmpDir,
            File(TEST_LOG_DIR),
            mockRetentionConfig)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "local_android_device_provider"
                }
                class_name: "com.google.testing.platform.runtime.android.provider.local.LocalAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_PROVIDER_LOCAL.jar"
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
                  label: "android_device_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "android_test_device_info_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.info.host.AndroidTestDeviceInfoPlugin"
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
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000"
                }
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
            mockSdkComponents,
            mockOutputDir,
            mockTmpDir,
            File(TEST_LOG_DIR),
            mockRetentionConfig)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              provider {
                label {
                  label: "local_android_device_provider"
                }
                class_name: "com.google.testing.platform.runtime.android.provider.local.LocalAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_PROVIDER_LOCAL.jar"
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
                  label: "icebox_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.icebox.host.IceboxPlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN_HOST_RETENTION.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.runner.plugin.android.icebox.host.proto.IceboxPlugin"
                  value: "\n\027com.example.application\022\tlocalhost\030\352B"
                }
              }
              host_plugin {
                label {
                  label: "android_device_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "android_test_device_info_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.info.host.AndroidTestDeviceInfoPlugin"
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
                  value: "\ns\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\017\022\r\n\005debug\022\004true"
                }
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
            "uniqueIdentifier",
            "path/to/logcat",
            "path/for/emulator/metadata",
            "path/to/emulator/script")
        val runnerConfigProto = factory.createRunnerConfigProtoForManagedDevice(
            managedDevice,
            testData,
            listOf(mockAppApk, mockTestApk, mockHelperApk),
            utpDependencies,
            mockSdkComponents,
            mockOutputDir,
            mockTmpDir,
            File(TEST_LOG_DIR),
            mockRetentionConfig
        )
        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "uniqueIdentifier"
              }
              provider {
                label {
                  label: "virtual_android_device_provider_config"
                }
                class_name: "com.google.testing.platform.runtime.android.provider.virtual.VirtualAndroidDeviceProvider"
                jar {
                  path: "pathToANDROID_DEVICE_PROVIDER_VIRTUAL.jar"
                }
                config {
                  type_url: "type.googleapis.com/google.testing.platform.proto.api.config.VirtualAndroidDeviceProviderConfig"
                  value: "\030\0018\001B\031\n\027path/to/emulator/scriptH\255\'P\002Z\r\n\vusr/bin/kvmb\005en_USx\350\a\200\001\a\220\001F\252\001\020\n\016path/to/logcat\330\001\001\352\001\034\n\032path/for/emulator/metadata"
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
                  label: "android_device_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
                jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
                }
              }
              host_plugin {
                label {
                  label: "android_test_device_info_plugin"
                }
                class_name: "com.google.testing.platform.plugin.android.info.host.AndroidTestDeviceInfoPlugin"
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
                  value: "\nd\n`\n\027com.example.application\022\034com.example.application.test\032\'androidx.test.runner.AndroidJUnitRunner\022\000"
                }
              }
            }
            single_device_executor {
              device_execution {
                device_id {
                  id: "uniqueIdentifier"
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
