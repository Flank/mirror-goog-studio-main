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
import com.android.build.gradle.integration.common.fixture.installPackage
import com.android.build.gradle.integration.common.fixture.uninstallPackage
import com.android.build.gradle.integration.connected.utils.getEmulator
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

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
        uninstallPackage("com.example.rubidumconsumer", ignoreErrors = true)
        uninstallPackage("com.example.rubidumconsumer.test", ignoreErrors = true)
        uninstallPackage("com.myrbsdk_10000", ignoreErrors = true)
    }

    @Test
    fun `connectedAndroidTest task`() {
        project.execute("connectedAndroidTest")
    }

    @Test
    @Ignore // This doesn't work because deployment is not handled yet.
    fun `install and uninstall works for both SDK and APK`() {
        project.execute("installDebug")
        // Verify both APKs are installed here.
        project.execute("uninstallAll")
        // Verify both APKs are deleted
    }

    companion object {
        @JvmField @ClassRule val emulator = getEmulator()
    }
}
