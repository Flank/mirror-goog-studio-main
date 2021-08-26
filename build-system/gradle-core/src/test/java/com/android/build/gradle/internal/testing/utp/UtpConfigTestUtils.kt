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

import com.android.tools.utp.plugins.deviceprovider.ddmlib.proto.AndroidDeviceProviderDdmlibConfigProto
import com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderProto
import com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfigProto
import com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfigProto
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfigProto
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.plugin.android.proto.AndroidDevicePluginProto
import com.google.testing.platform.proto.api.config.AndroidInstrumentationDriverProto
import com.google.testing.platform.proto.api.config.LocalAndroidDeviceProviderProto
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.PathProto

private val protoPrinter: ProtoPrinter = ProtoPrinter(listOf(
    AndroidAdditionalTestOutputConfigProto.AndroidAdditionalTestOutputConfig::class.java,
    AndroidDevicePluginProto.AndroidDevicePlugin::class.java,
    AndroidDeviceProviderDdmlibConfigProto.DdmlibAndroidDeviceProviderConfig::class.java,
    AndroidInstrumentationDriverProto.AndroidInstrumentationDriver::class.java,
    AndroidTestCoverageConfigProto.AndroidTestCoverageConfig::class.java,
    GradleAndroidTestResultListenerConfigProto.GradleAndroidTestResultListenerConfig::class.java,
    GradleManagedAndroidDeviceProviderProto.GradleManagedAndroidDeviceProviderConfig::class.java,
    IceboxPluginProto.IceboxPlugin::class.java,
    LocalAndroidDeviceProviderProto.LocalAndroidDeviceProvider::class.java,
    PathProto.Path::class.java,
))

/**
 * Asserts that a given [runnerConfig] matches to a given list of configurations.
 */
fun assertRunnerConfigProto(
    runnerConfig: RunnerConfigProto.RunnerConfig,
    deviceId: String = "mockDeviceSerialNumber",
    useOrchestrator: Boolean = false,
    useTestStorageService: Boolean = false,
    noWindowAnimation: Boolean = false,
    instrumentationArgs: Map<String, String> = mapOf(),
    iceboxConfig: String = "",
    useGradleManagedDeviceProvider: Boolean = false,
    testCoverageConfig: String = "",
    additionalTestOutputConfig: String = "",
    shardingConfig: String = "",
    uninstallIncompatibleApks: Boolean = false,
) {
    val deviceProviderProto = if (useGradleManagedDeviceProvider) { """
        label {
          label: "ANDROID_DEVICE_PROVIDER_GRADLE"
        }
        class_name: "com.android.tools.utp.plugins.deviceprovider.gradle.GradleManagedAndroidDeviceProvider"
        jar {
          path: "path-to-DeviceProviderGradle.jar"
        }
        config {
          type_url: "type.googleapis.com/com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderConfig"
          value {
            managed_device {
              avd_folder {
                type_url: "type.googleapis.com/google.testing.platform.proto.api.core.Path"
                value {
                  path: "path/to/gradle/avd"
                }
              }
              avd_name: "avdName"
              avd_id: "${deviceId}"
              emulator_path {
                type_url: "type.googleapis.com/google.testing.platform.proto.api.core.Path"
                value {
                  path: "path/to/emulator"
                }
              }
              gradle_dsl_device_name: "deviceName"
            }
            adb_server_port: 5037
          }
        }
        """
    } else { """
        label {
          label: "ANDROID_DEVICE_PROVIDER_DDMLIB"
        }
        class_name: "com.android.tools.utp.plugins.deviceprovider.ddmlib.DdmlibAndroidDeviceProvider"
        jar {
          path: "path-to-DeviceControllerDdmlib.jar"
        }
        config {
          type_url: "type.googleapis.com/com.android.tools.utp.plugins.deviceprovider.ddmlib.proto.DdmlibAndroidDeviceProviderConfig"
          value {
            local_android_device_provider_config {
              type_url: "type.googleapis.com/google.testing.platform.proto.api.config.LocalAndroidDeviceProvider"
              value {
                serial: "${deviceId}"
              }
            }
            ${if (uninstallIncompatibleApks)  "uninstall_incompatible_apks: true"  else ""}
          }
        }
        """
    }

    val testCoveragePluginProto = if (testCoverageConfig.isNotBlank()) { """
        host_plugin {
          label {
            label: "ANDROID_TEST_COVERAGE_PLUGIN"
          }
          class_name: "com.android.tools.utp.plugins.host.coverage.AndroidTestCoveragePlugin"
          jar {
            path: "path-to-TestCoveragePlugin.jar"
          }
          config {
            type_url: "type.googleapis.com/com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfig"
            value {
              ${"\n" + testCoverageConfig.trimIndent().prependIndent(" ".repeat(14))}
            }
          }
        }
        """
    } else {
        ""
    }

    val iceboxPluginProto = if (iceboxConfig.isNotBlank()) { """
        host_plugin {
          label {
            label: "ANDROID_TEST_PLUGIN_HOST_RETENTION"
          }
          class_name: "com.android.tools.utp.plugins.host.icebox.IceboxPlugin"
          jar {
            path: "path-to-TestPluginHostRetention.jar"
          }
          config {
            type_url: "type.googleapis.com/com.android.tools.utp.plugins.host.icebox.proto.IceboxPlugin"
            value {
              ${"\n" + iceboxConfig.trimIndent().prependIndent(" ".repeat(14))}
            }
          }
        }
        """
    } else {
        ""
    }

    val additionalTestOutputConfigProto = if (additionalTestOutputConfig.isNotBlank()) { """
        host_plugin {
          label {
            label: "ANDROID_TEST_ADDITIONAL_TEST_OUTPUT_PLUGIN"
          }
          class_name: "com.android.tools.utp.plugins.host.additionaltestoutput.AndroidAdditionalTestOutputPlugin"
          jar {
            path: "path-to-AdditionalTestOutputPlugin.jar"
          }
          config {
            type_url: "type.googleapis.com/com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfig"
            value {
              ${"\n" + additionalTestOutputConfig.trimIndent().prependIndent(" ".repeat(14))}
            }
          }
        }
        """
    } else {
        ""
    }

    val shardingConfigProto = if (shardingConfig.isNotBlank()) { """
        sharding_config {
          ${"\n" + shardingConfig.trimIndent().prependIndent(" ".repeat(10))}
        }
    """
    } else {
        ""
    }

    assertThat(protoPrinter.printToString(runnerConfig)).isEqualTo("""
        device {
          device_id {
            id: "${deviceId}"
          }
          provider {
            ${"\n" + deviceProviderProto.trimIndent().prependIndent(" ".repeat(12))}
          }
        }
        test_fixture {
          test_fixture_id {
            id: "AGP_Test_Fixture"
          }
          ${"\n" + iceboxPluginProto.trimIndent().prependIndent(" ".repeat(10))}
          host_plugin {
            label {
              label: "ANDROID_TEST_PLUGIN"
            }
            class_name: "com.google.testing.platform.plugin.android.AndroidDevicePlugin"
            jar {
              path: "path-to-TestPlugin.jar"
            }
            config {
              type_url: "type.googleapis.com/google.testing.platform.runner.plugin.android.proto.AndroidDevicePlugin"
              value {
                test_service_apks {
                  source_path {
                    path: "mockHelperApkPath"
                  }
                  type: ANDROID_APK
                }
                test_apks {
                  test_apk {
                    source_path {
                      path: "testApk.apk"
                    }
                    type: ANDROID_APK
                  }
                  install_options {
                    command_line_parameter: "-additional_install_option"
                  }
                }
                test_apks {
                  test_apk {
                    source_path {
                      path: "mockAppApkPath"
                    }
                    type: ANDROID_APK
                  }
                  install_options {
                    command_line_parameter: "-additional_install_option"
                  }
                }
                test_apks {
                  test_apk {
                    source_path {
                      path: "mockTestApkPath"
                    }
                    type: ANDROID_APK
                  }
                  install_options {
                    command_line_parameter: "-additional_install_option"
                  }
                }
              }
            }
          }
          host_plugin {
            label {
              label: "ANDROID_TEST_DEVICE_INFO_PLUGIN"
            }
            class_name: "com.android.tools.utp.plugins.host.device.info.AndroidTestDeviceInfoPlugin"
            jar {
              path: "path-to-TestDeviceInfoPlugin.jar"
            }
          }
          host_plugin {
            label {
              label: "ANDROID_TEST_LOGCAT_PLUGIN"
            }
            class_name: "com.android.tools.utp.plugins.host.logcat.AndroidTestLogcatPlugin"
            jar {
              path: "path-to-TestLogcatPlugin.jar"
            }
          }
          ${"\n" + testCoveragePluginProto.trimIndent().prependIndent(" ".repeat(10))}
          ${"\n" + additionalTestOutputConfigProto.trimIndent().prependIndent(" ".repeat(10))}
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
              path: "path-to-DriverInstrumentation.jar"
            }
            config {
              type_url: "type.googleapis.com/google.testing.platform.proto.api.config.AndroidInstrumentationDriver"
              value {
                android_instrumentation_runtime {
                  instrumentation_info {
                    app_package: "com.example.application"
                    test_package: "com.example.application.test"
                    test_runner_class: "androidx.test.runner.AndroidJUnitRunner"
                  }
                  instrumentation_args {
                    ${instrumentationArgs.map { (key, value) -> """
                    args_map {
                      key: "${key}"
                      value: "${value}"
                    }
                    """}.joinToString("\n")}
                    ${if (noWindowAnimation) "no_window_animation: true" else ""}
                    ${if (useTestStorageService) "use_test_storage_service: true" else ""}
                  }
                }
                am_instrument_timeout: 31536000
                ${if (useOrchestrator) "use_orchestrator: true" else "" }
              }
            }
          }
        }
        test_result_listener {
          label {
            label: "ANDROID_TEST_PLUGIN_RESULT_LISTENER_GRADLE"
          }
          class_name: "com.android.tools.utp.plugins.result.listener.gradle.GradleAndroidTestResultListener"
          jar {
            path: "path-to-TestPluginResultListenerGradle.jar"
          }
          config {
            type_url: "type.googleapis.com/com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerConfig"
            value {
              resultListenerServerPort: 1234
              resultListenerClientCertFilePath: "mockResultListenerClientCertPath"
              resultListenerClientPrivateKeyFilePath: "mockResultListenerClientPrivateKeyPath"
              trustCertCollectionFilePath: "mockTrustCertCollectionPath"
              deviceId: "${deviceId}"
            }
          }
        }
        single_device_executor {
          device_execution {
            device_id {
              id: "${deviceId}"
            }
            test_fixture_id {
              id: "AGP_Test_Fixture"
            }
          }
          ${"\n" + shardingConfigProto.trimIndent().prependIndent(" ".repeat(10))}
        }
        """.trimIndent().lines().filter(String::isNotBlank).joinToString("\n"))
}
