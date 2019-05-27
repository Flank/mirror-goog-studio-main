/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AllTasksUpToDateTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile, """
                 android.buildTypes {
                       release { minifyEnabled true }
                       r8 { minifyEnabled true; useProguard false }
                 }""".trimIndent()
        )
    }

    @Test
    fun allTasksUpToDate() {
        val tasksToRun = arrayOf("build", "assembleAndroidTest")

        project.execute(*tasksToRun)
        val result = project.executor().run(*tasksToRun)

        Truth.assertThat(result.didWorkTasks)
            // Known exceptions:
            .containsExactly(
                // Lint declares no outputs, so it's never up-to-date. It's probably for the
                // better, because it's hard to declare all inputs (they include the SDK
                // and contents of the Google maven repo).
                ":lint"
            )
    }
}
