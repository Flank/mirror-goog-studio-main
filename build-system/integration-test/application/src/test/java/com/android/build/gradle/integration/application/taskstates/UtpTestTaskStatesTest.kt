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
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import net.bytebuddy.matcher.ElementMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.IOException

private fun assertTaskExists(
    project: GradleTestProject,
    task: String) {

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
    var project = EmptyActivityProjectBuilder()
        .also { it.withConfigurationCaching = BaseGradleExecutor.ConfigurationCaching.OFF }
        .build()

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
                }
            }
        """)
        assertTaskDoesNotExist(project, "app:cleanManagedDevices")
        assertTaskDoesNotExist(project, "app:device1Setup")
    }

    @Test
    fun checkUtpFlagAddsCleanTask() {
        project.gradlePropertiesFile.appendText(
            "\nandroid.experimental.androidTest.useUnifiedTestPlatform=true\n")

        assertTaskExists(project, "app:cleanManagedDevices")
    }

    @Test
    fun checkDslAddsSetupTasksWithFlag() {
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
                }
            }
        """)
        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:device1Setup")
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
                }
            }
        """)
        assertTaskExists(project, "app:cleanManagedDevices")
        assertTaskExists(project, "app:device1Setup")
        assertTaskExists(project, "app:someDeviceNameSetup")
    }
}
