/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class ProjectNoJavaSourcesTest(val testProject: MinimalSubProject) {

    companion object {
        @Parameterized.Parameters
        @JvmStatic
        fun parameters() =
            listOf(MinimalSubProject.app("com.test"), MinimalSubProject.lib("com.test"))
    }

    @get: Rule
    val project = GradleTestProject.builder().fromTestApp(
        testProject.appendToBuild(
            """
            android.buildFeatures.buildConfig = false
        """.trimIndent()
        )
    ).create()

    @Test
    fun testBuild() {
        executor().run("assemble", "assembleDebugAndroidTest")
        Truth.assertThat(project.projectDir.walk().filter { it.extension == "java" }
            .toList()).named("list of Java sources").isEmpty()
    }

    @Test
    fun testMinified() {
        project.buildFile.appendText(
            """
            android {
              buildTypes {
                debug {
                  minifyEnabled true
                }
              }
            }
        """.trimIndent()
        )
        executor().run("assemble", "assembleDebugAndroidTest")
        Truth.assertThat(project.projectDir.walk().filter { it.extension == "java" }
            .toList()).named("list of Java sources").isEmpty()
    }

    @Test
    fun testLegacyMultidex() {
        project.buildFile.appendText(
            """
            android {
              defaultConfig {
                minSdkVersion 19
                multiDexEnabled true
              }
            }
        """.trimIndent()
        )
        executor().run("assemble", "assembleDebugAndroidTest")
        Truth.assertThat(project.projectDir.walk().filter { it.extension == "java" }
            .toList()).named("list of Java sources").isEmpty()
    }

    private fun executor() : GradleTaskExecutor {
        return if (testProject.plugin == "com.android.application") {
            // http://b/149978740
             project.executor().with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
        } else {
             project.executor()
        }
    }
}
