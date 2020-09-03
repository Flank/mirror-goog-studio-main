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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DataBindingInstantExecutionTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("databinding")
        .create()

    @Before
    fun setUp() {
        project.projectDir.resolve(".gradle/configuration-cache").deleteRecursively()

        // Disable lint because of http://b/146208910
        project.buildFile.appendText("""

            android {
                lintOptions {
                    checkReleaseBuilds false
                }
            }
        """.trimIndent())
    }

    @Test
    fun testCleanBuild() {
        executor().run("assemble")
        executor().run("clean")
        executor().run("assemble")
    }

    private fun executor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
}