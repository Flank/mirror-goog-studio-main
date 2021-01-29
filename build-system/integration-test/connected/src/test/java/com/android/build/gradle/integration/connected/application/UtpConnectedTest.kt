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
import com.android.build.gradle.integration.connected.utils.getEmulator
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Connected tests using UTP test executor.
 */
class UtpConnectedTest {

    companion object {
        @ClassRule
        @JvmField
        val EMULATOR = getEmulator()
    }

    @get:Rule
    var project = builder()
            .fromTestProject("utp")
            .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTest() {
        project.executor().run("connectedAndroidTest")

        // Run the task again after clean. This time the task configuration is
        // restored from the configuration cache. We expect no crashes.
         project.executor().run("clean")
         project.executor().run("connectedAndroidTest")
    }
}
