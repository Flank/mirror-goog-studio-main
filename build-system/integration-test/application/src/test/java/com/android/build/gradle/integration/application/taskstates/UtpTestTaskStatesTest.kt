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

package com.android.build.gradle.integration.application.taskstates

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private fun assertTaskExists(project: GradleTestProject, task: String) {
    project.executor()
        .withArgument("--dry-run")
        .run(task)
}

private fun assertTaskDoesNotExist(
    project: GradleTestProject,
    task: String) {

    project.executor()
        .expectFailure()
        .withArgument("--dry-run")
        .run(task)
}

/**
 * Verifies Unified Test Platform task creation when Devices are modified from the DSL.
 */
@RunWith(JUnit4::class)
class UtpTestTaskStatesTest {

    @get:Rule
    var project = EmptyActivityProjectBuilder().build()

    lateinit var appProject: GradleTestProject

    @Before
    fun setup() {
        appProject = project.getSubproject("app")
    }

    @Test
    fun checkNoUtpTasksDefault() {
        assertTaskDoesNotExist(project, "cleanManagedDevices")
    }

    @Test
    fun checkNoUtpTasksWithoutFlag() {
        appProject.buildFile.appendText("""
            android {
                testOptions {
                    devices {
                        device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                            device = "Pixel 2"
                            apiLevel = 29
                            systemImageSource = "aosp"
                            abi = "x86"
                        }
                    }
                    execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
            }
        """)
        assertTaskDoesNotExist(project, "app:cleanManagedDevices")
        assertTaskDoesNotExist(project, "app:device1Setup")
        assertTaskDoesNotExist(project, "app:device1DebugAndroidTest")
        assertTaskDoesNotExist(project, "app:allDevicesDebugAndroidTest")
        assertTaskDoesNotExist(project, "app:device1Check")
        assertTaskDoesNotExist(project, "app:allDevicesCheck")
    }

    @Test
    fun checkUtpFlagAddsCleanAndAllDevicesTask() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.androidTest.useUnifiedTestPlatform=true\n")

        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:allDevicesDebugAndroidTest")
        assertTaskExists(project, "app:allDevicesCheck")
    }

    @Test
    fun checkDslAddsSetupAndTestTasksWithFlag() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.androidTest.useUnifiedTestPlatform=true\n")
        appProject.buildFile.appendText("""
            android {
                testOptions {
                    devices {
                        device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                            device = "Pixel 2"
                            apiLevel = 29
                            systemImageSource = "aosp"
                            abi = "x86"
                        }
                    }
                    execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
            }
        """)
        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:device1Setup")
        assertTaskExists(project, "app:device1DebugAndroidTest")
        assertTaskExists(project, "app:allDevicesDebugAndroidTest")
        assertTaskExists(project, "app:device1Check")
        assertTaskExists(project, "app:allDevicesCheck")
    }

    @Test
    fun checkDslSupportsMultipleDevices() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.androidTest.useUnifiedTestPlatform=true\n")
        appProject.buildFile.appendText("""
            android {
                testOptions {
                    devices {
                        device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                            device = "Pixel 2"
                            apiLevel = 29
                            systemImageSource = "aosp"
                            abi = "x86"
                        }
                        someDeviceName (com.android.build.api.dsl.ManagedVirtualDevice) {
                            device = "Pixel 3"
                            apiLevel = 27
                            systemImageSource = "aosp"
                            abi = "x86"
                        }
                    }
                    execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
            }
        """)
        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:device1Setup")
        assertTaskExists(project, "app:someDeviceNameSetup")
        assertTaskExists(project, "app:device1DebugAndroidTest")
        assertTaskExists(project, "app:someDeviceNameDebugAndroidTest")
        assertTaskExists(project, "app:allDevicesDebugAndroidTest")
        assertTaskExists(project, "app:device1Check")
        assertTaskExists(project, "app:someDeviceNameCheck")
        assertTaskExists(project, "app:allDevicesCheck")
    }

    @Test
    fun checkAddVariantAddsTests() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.androidTest.useUnifiedTestPlatform=true\n")
        appProject.buildFile.appendText("""
            android {
                flavorDimensions "version"
                productFlavors {
                    demo {
                        dimension = "version"
                        applicationIdSuffix = ".demo"
                        versionNameSuffix = "-demo"
                    }
                    full {
                        dimension = "version"
                        applicationIdSuffix = ".full"
                        versionNameSuffix = "-full"
                    }
                }
                testOptions {
                    devices {
                        device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                            device = "Pixel 2"
                            apiLevel = 29
                            systemImageSource = "aosp"
                            abi = "x86"
                        }
                    }
                    execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
            }
        """)

        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:device1Setup")
        assertTaskExists(project, "app:device1DemoDebugAndroidTest")
        assertTaskExists(project, "app:allDevicesDemoDebugAndroidTest")
        assertTaskExists(project, "app:device1FullDebugAndroidTest")
        assertTaskExists(project, "app:allDevicesFullDebugAndroidTest")
        assertTaskExists(project, "app:device1Check")
        assertTaskExists(project, "app:allDevicesCheck")
    }

    @Test
    fun checkDeviceGroupTasks() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.androidTest.useUnifiedTestPlatform=true\n")
        appProject.buildFile.appendText("""
            android {
                testOptions {
                    devices {
                        device1 (com.android.build.api.dsl.ManagedVirtualDevice) {
                            device = "Pixel 2"
                            apiLevel = 29
                            systemImageSource = "aosp"
                            abi = "x86"
                        }
                    }
                    deviceGroups {
                        test {
                            targetDevices.add(devices.device1)
                        }
                    }
                    execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
            }
        """)
        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:device1Setup")
        assertTaskExists(project, "app:device1DebugAndroidTest")
        assertTaskExists(project, "app:testGroupDebugAndroidTest")
        assertTaskExists(project, "app:allDevicesDebugAndroidTest")
        assertTaskExists(project, "app:device1Check")
        assertTaskExists(project, "app:testGroupCheck")
        assertTaskExists(project, "app:allDevicesCheck")
    }
}
