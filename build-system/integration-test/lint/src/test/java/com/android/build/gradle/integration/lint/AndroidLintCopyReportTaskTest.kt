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
import com.android.build.gradle.internal.lint.AndroidLintCopyReportTask
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration test for [AndroidLintCopyReportTask]
 */
@RunWith(FilterableParameterized::class)
class AndroidLintCopyReportTaskTest(private val usePartialAnalysis: Boolean) {

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
                                    textOutput file("lint-results.txt")
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    // Regression test for b/189877657
    @Test
    fun testRunningTaskDirectly() {
        project.getExecutor().run("clean", "copyDebugAndroidLintReports")
        ScannerSubject.assertThat(project.buildResult.stdout).contains("BUILD SUCCESSFUL")
        ScannerSubject.assertThat(project.buildResult.stdout)
            .contains("Unable to copy the lint text report")
    }

    @Test
    fun testReportCopiedAfterLint() {
        project.getExecutor().run("clean", "lintDebug")
        assertThat(project.file("lint-results.txt")).exists()
    }

    private fun GradleTestProject.getExecutor(): GradleTaskExecutor =
        this.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
