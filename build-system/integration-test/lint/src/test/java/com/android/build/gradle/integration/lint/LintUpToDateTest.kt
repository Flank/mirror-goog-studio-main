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
package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Integration test that lint can be up-to-date  */
@RunWith(FilterableParameterized::class)
class LintUpToDateTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withConfigurationCacheMaxProblems(4)
            .fromTestProject("lintKotlin")
            .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun before() {
        // need checkDependencies false if not using partial analysis because the lint task is never
        // up-to-date when checkDependencies is true when not using partial analysis.
        project.getSubproject(":app")
            .buildFile
            .appendText(
                """
                    android {
                        lintOptions {
                            abortOnError false
                            checkDependencies $usePartialAnalysis
                        }
                    }
                """.trimIndent()
            )
        project.executor().run(":app:cleanLintDebug")
    }

    @Test
    fun checkLintUpToDate() {
        val firstRun = getExecutor().run(":app:lintDebug")

        assertThat(firstRun.getTask(":app:lintDebug")).didWork()
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("8 errors")

        val secondRun = getExecutor().run(":app:lintDebug")
        assertThat(secondRun.getTask(":app:lintDebug")).wasUpToDate()
        if (usePartialAnalysis) {
            assertThat(secondRun.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        }
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
