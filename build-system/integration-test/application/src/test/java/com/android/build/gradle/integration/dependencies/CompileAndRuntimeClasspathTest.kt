/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class CompileAndRuntimeClasspathTest {
    @JvmField
    @Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Test
    fun testDifferentCompileAndRuntimeCauseFailure() {
        project.buildFile.appendText(
            """
            |dependencies {
            |    compileOnly'com.google.guava:guava:19.0'
            |    runtimeOnly'com.google.guava:guava:20.0'
            |}""".trimMargin()
        )

        val result = project.executor().run("checkDebugClasspath")
        assertThat(result.stdout).contains("Resolved versions for runtime classpath (20.0) and compile classpath (19.0) differ.")
    }
}