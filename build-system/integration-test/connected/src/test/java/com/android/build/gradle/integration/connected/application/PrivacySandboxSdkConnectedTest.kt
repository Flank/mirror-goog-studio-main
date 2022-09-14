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

package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.executeShellCommand
import com.android.build.gradle.integration.common.fixture.uninstallPackage
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.ddmlib.MultiLineReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

private const val APP_PACKAGE_NAME = "com.example.rubidumconsumer"
private const val SDK_PACKAGE_NAME = "com.myrbsdk_10000"
private const val TEST_PACKAGE_NAME = "com.example.rubidumconsumer.test"

/**
 * Connected tests using UTP test executor.
 */
class PrivacySandboxSdkConnectedTest {

    @get:Rule var project = builder()
        .fromTestProject("privacySandboxSdk/libraryAndConsumer")
        .create()

    @Before
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();

        println("Uninstalling packages")
        uninstallIfExists(APP_PACKAGE_NAME)
        uninstallIfExists(TEST_PACKAGE_NAME)
        uninstallIfExists(SDK_PACKAGE_NAME, isLibrary = true)
    }

    @Test
    fun `connectedAndroidTest task for application`() {
        project.execute(":app:connectedAndroidTest")
    }

    @Test
    fun `connectedAndroidTest task for application with dynamic feature`() {
        project.execute(":app-with-dynamic-feature:connectedAndroidTest")
    }

    @Test
    fun `install and uninstall works for both SDK and APK for application`() {
        project.execute(":app:installDebug")
        assertThat(packageExists(APP_PACKAGE_NAME)).isTrue()
        assertThat(packageExists(SDK_PACKAGE_NAME, isLibrary = true)).isTrue()

        // project.execute(":app:uninstallAll")
        // TODO: uninstall not supported yet, verify both APKs are deleted here
    }

    @Test
    fun `install and uninstall works for both SDK and APK for application with dynamic feature`() {
        project.execute(":app-with-dynamic-feature:installDebug")
        assertThat(packageExists(APP_PACKAGE_NAME)).isTrue()
        assertThat(packageExists(SDK_PACKAGE_NAME, isLibrary = true)).isTrue()

        // project.execute(":app-with-dynamic-feature:uninstallAll")
        // TODO: uninstall not supported yet, verify both APKs are deleted here
    }

    private fun packageExists(packageName: String, isLibrary: Boolean = false) : Boolean {
        val type =  if (isLibrary) "libraries" else "packages"
        return executeShellCommand("pm", "list" ,type)
            .lines()
            .map { it.substringAfter(":") }
            // Libraries listed here don't have the version number after the _
            // So that part is stripped out
            .any { it == packageName.substringBefore("_") }
    }

    private fun uninstallIfExists(packageName: String, isLibrary: Boolean = false) {
        if (packageExists(packageName, isLibrary = isLibrary)) {
            uninstallPackage(packageName)
        }
    }


    companion object {
        @JvmField @ClassRule val emulator = getEmulator()
    }
}
