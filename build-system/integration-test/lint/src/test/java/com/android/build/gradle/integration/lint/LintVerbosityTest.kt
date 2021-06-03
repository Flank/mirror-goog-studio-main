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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration test testing lint verbosity.
 */
@RunWith(FilterableParameterized::class)
class LintVerbosityTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                        """
                            android {
                                lintOptions {
                                    abortOnError false
                                    quiet false
                                    error 'AllowBackup'
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    @Test
    fun testQuiet() {
        // first check that we see "Scanning" in stdout if running with --info and quiet=false
        project.getExecutor().withArgument("--info").run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).contains("Scanning ")
        // then set quiet to true and check that stdout doesn't contain "Scanning".
        TestFileUtils.searchAndReplace(project.buildFile, "quiet false", "quiet true")
        project.getExecutor().withArgument("--info").run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain("Scanning ")
    }

    // Regression test for b/187329866
    @Test
    fun testErrorMessage() {
        TestFileUtils.searchAndReplace(project.buildFile, "abortOnError false", "abortOnError true")
        project.getExecutor().expectFailure().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stderr).contains(
            """
                Lint found errors in the project; aborting build.

                Fix the issues identified by lint, or add the following to your build script to proceed with errors:
                ...
                android {
                    lintOptions {
                        abortOnError false
                    }
                }
                ...

            """.trimIndent()
        )
    }

    private fun GradleTestProject.getExecutor(): GradleTaskExecutor =
        this.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
