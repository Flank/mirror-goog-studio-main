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

import com.android.build.gradle.internal.SdkComponents
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.sdklib.BuildToolInfo
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.TextFormat
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File

/**
 * Unit tests for [UtpConfigFactory].
 */
class UtpConfigFactoryTest {
    @get:Rule var mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock lateinit var mockConfigurationContainer: ConfigurationContainer
    @Mock lateinit var mockSdkComponents: SdkComponents
    @Mock
    lateinit var mockTestData: StaticTestData
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
    lateinit var mockTestLogDir: File
    @Mock
    lateinit var mockRunLogDir: File
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

    @Before
    fun setupMocks() {
        `when`(mockTestData.testedApplicationId).thenReturn("com.example.application")
        `when`(mockTestData.applicationId).thenReturn("com.example.application.test")
        `when`(mockTestData.instrumentationRunner).thenReturn("androidx.test.runner.AndroidJUnitRunner")
        `when`(mockDevice.serialNumber).thenReturn("mockDeviceSerialNumber")
        `when`(mockConfigurationContainer.getByName(ArgumentMatchers.anyString())).then {
            val configName = it.getArgument<String>(0)
            UtpDependency.values()
                    .filter { it.configurationName == configName }
                    .map {
                        val mockJar = Mockito.mock(File::class.java)
                        `when`(mockJar.absolutePath).thenReturn("pathTo${it.name}.jar")
                        val mockConfig = Mockito.mock(Configuration::class.java)
                        `when`(mockConfig.files).thenReturn(setOf(mockJar))
                        `when`(mockConfig.singleFile).thenReturn(mockJar)
                        mockConfig
                    }
                    .firstOrNull()
        }
        `when`(mockOutputDir.absolutePath).thenReturn("mockOutputDirPath")
        `when`(mockTmpDir.absolutePath).thenReturn("mockTmpDirPath")
        `when`(mockTestLogDir.absolutePath).thenReturn("mockTestLogDirPath")
        `when`(mockRunLogDir.absolutePath).thenReturn("mockRunLogDirPath")
        `when`(mockAppApk.absolutePath).thenReturn("mockAppApkPath")
        `when`(mockTestApk.absolutePath).thenReturn("mockTestApkPath")
        `when`(mockHelperApk.absolutePath).thenReturn("mockHelperApkPath")
        `when`(mockSdkComponents.getSdkDirectory()).thenReturn(mockSdkDir)
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
    fun createRunnerConfigProto() {
        val factory = UtpConfigFactory()
        val runnerConfigProto = factory.createRunnerConfigProto(
                mockDevice,
                mockTestData,
                listOf(mockAppApk, mockTestApk, mockHelperApk),
                mockConfigurationContainer,
                mockSdkComponents,
                mockOutputDir,
                mockTmpDir,
                mockTestLogDir,
                mockRunLogDir)

        assertThat(runnerConfigProto.toString()).isEqualTo("""
            device {
              device_id {
                id: "mockDeviceSerialNumber"
              }
              controller {
                controller_class: "com.google.test.platform.runtime.android.adb.controller.AdbDeviceController"
                controller_jar {
                  path: "pathToANDROID_DEVICE_CONTROLLER_ADB.jar"
                }
                device_controller_config {
                  type_url: "type.googleapis.com/com.google.test.platform.config.v1.proto.AdbDeviceController"
                }
              }
              provider {
                provider_class: "com.google.test.platform.runtime.android.provider.local.LocalAndroidDeviceProvider"
                provider_jar {
                  path: "pathToANDROID_DEVICE_PROVIDER_LOCAL.jar"
                }
                device_provider_config {
                  type_url: "type.googleapis.com/com.google.test.platform.config.v1.proto.LocalAndroidDeviceProvider"
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
                plugin_class: "com.google.test.platform.plugin.android.AndroidDevicePlugin"
                plugin_jar {
                  path: "pathToANDROID_TEST_PLUGIN.jar"
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
                    path: "mockTestLogDirPath"
                  }
                  test_run_log {
                    path: "mockRunLogDirPath"
                  }
                }
              }
              test_driver {
                label {
                  label: "ANDROID_DRIVER_INSTRUMENTATION"
                }
                class_name: "com.google.test.platform.runtime.android.driver.OrchestratorV1Driver"
                jar {
                  path: "pathToANDROID_DRIVER_INSTRUMENTATION.jar"
                }
                config {
                  type_url: "type.googleapis.com/com.google.test.platform.config.v1.proto.OrchestratorV1Driver"
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
    fun createServerConfigProto() {
        val factory = UtpConfigFactory()
        val serverConfigProto = factory.createServerConfigProto()

        assertThat(TextFormat.printToString(serverConfigProto)).isEqualTo("""
            address: "localhost:20000"

        """.trimIndent())
    }
}