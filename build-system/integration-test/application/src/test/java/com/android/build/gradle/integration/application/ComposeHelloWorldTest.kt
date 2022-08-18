/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ComposeHelloWorldTest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestProject("composeHelloWorld")
            .withKotlinVersion(KOTLIN_VERSION_FOR_COMPOSE_TESTS)
            .create()

    @Test
    fun appAndTestsBuildSuccessfully() {
        val executor = project.executor()

        val tasks = listOf("clean",  "assembleDebug", "assembleDebugAndroidTest")
        executor.run(tasks)
        // run once again to test configuration caching
        executor.run(tasks)
    }

    @Test
    fun testLiveLiterals() {
        val executor = project.executor()

        // Run compilation with live literals on
        TestFileUtils.appendToFile(project.getSubproject("app").buildFile,
                "android.composeOptions.useLiveLiterals = true")
        executor.run("assembleDebug")

        // Turn off live literals and run again
        TestFileUtils.searchAndReplace(project.getSubproject("app").buildFile,
                "android.composeOptions.useLiveLiterals = true",
                "android.composeOptions.useLiveLiterals = false")
        val result = executor.run("assembleDebug")
        assertThat(result.didWorkTasks).contains(":app:compileDebugKotlin")
    }
}
