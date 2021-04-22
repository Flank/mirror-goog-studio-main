/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(FilterableParameterized::class)
class LintCompileOnlyTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            textOutput file("lint-results.txt")
                            error 'StopShip'
                        }
                    }
                """.trimIndent()
            )

    private val lib =
        MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                """
                    android {
                        lintOptions {
                            error 'StopShip'
                        }
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/lib/Foo.java",
                """
                    package com.example.lib;

                    public class Foo {
                        // STOPSHIP
                    }
                """.trimIndent()
            )

    private val javaLib =
        MinimalSubProject.javaLibrary()
            .appendToBuild(
                """
                    apply plugin: 'com.android.lint'

                    lintOptions {
                        error 'StopShip'
                    }
                """.trimIndent()
            ).withFile(
                "src/main/java/com/example/Bar.java",
                """
                        package com.example;

                        public class Bar {
                            // STOPSHIP
                        }
                    """.trimIndent()
            )

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib", lib)
                    .subproject(":javaLib", javaLib)
                    .dependency("compileOnly", app, lib)
                    .dependency("compileOnly", app, javaLib)
                    .build()
            )
            .create()

    // Regression test for b/185232013
    @Test
    fun testLintWithCompileOnlyDependencies() {
        // Run twice to catch issues with configuration caching
        getExecutor().run("clean", ":app:lint")
        getExecutor().run("clean", ":app:lint")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
    }

    @Test
    fun testLintCheckDependenciesWithCompileOnlyDependencies() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            """
                    android {
                        lintOptions {
                            checkDependencies true
                            abortOnError false
                        }
                    }
                    """.trimIndent()
        )
        // Run twice to catch issues with configuration caching
        getExecutor().run("clean", ":app:lintRelease")
        getExecutor().run("clean", ":app:lintRelease")
        Truth.assertThat(project.buildResult.failedTasks).isEmpty()
        val reportFile = File(project.getSubproject(":app").projectDir, "lint-results.txt")
        PathSubject.assertThat(reportFile).exists()
        PathSubject.assertThat(reportFile).containsAllOf(
            "Foo.java:4: Error: STOPSHIP comment found",
            "Bar.java:4: Error: STOPSHIP comment found"
        )
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
