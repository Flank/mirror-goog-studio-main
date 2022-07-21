/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test for updating lint baselines using the updateLintBaseline task.
 */
class UpdateLintBaselineTest {

    @get:Rule
    val project = builder().fromTestProject("lintBaseline").create()

    @get:Rule
    val projectWithoutIssues =
        builder().fromTestApp(MinimalSubProject.app("com.example.app"))
            .withName("projectWithoutIssues")
            .create()

    @Test
    fun checkUpdateLintBaseline() {
        // This test runs updateLintBaseline in 6 scenarios:
        //   (1)  when there is no existing baseline,
        //   (2)  when the task is UP-TO-DATE,
        //   (3)  when there is already a correct existing baseline,
        //   (4)  when there is already an incorrect existing baseline,
        //   (5)  when a user runs updateLintBase and lint at the same time.
        //   (6)  when there is no baseline file specified.

        // First test the case when there is no existing baseline.
        val baselineFile = File(project.getSubproject("app").projectDir, "lint-baseline.xml")
        PathSubject.assertThat(baselineFile).doesNotExist()
        val result1 = project.executor().run(":app:updateLintBaseline")
        GradleTaskSubject.assertThat(result1.getTask(":app:lintAnalyzeDebug")).didWork()
        GradleTaskSubject.assertThat(result1.getTask(":app:updateLintBaselineDebug")).didWork()
        PathSubject.assertThat(baselineFile).exists()

        val baselineFileContents = baselineFile.readBytes()

        // Then test the case when the task is UP-TO-DATE.
        val result2 = project.executor().run(":app:updateLintBaseline")
        GradleTaskSubject.assertThat(result2.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        GradleTaskSubject.assertThat(result2.getTask(":app:updateLintBaselineDebug")).wasUpToDate()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is already a correct existing baseline.
        project.executor().run("clean")
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)
        val result3 = project.executor().run(":app:updateLintBaseline")
        GradleTaskSubject.assertThat(result3.getTask(":app:lintAnalyzeDebug")).didWork()
        GradleTaskSubject.assertThat(result3.getTask(":app:updateLintBaselineDebug")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is already an incorrect existing baseline.
        baselineFile.writeText("invalid")
        assertThat(baselineFile.readBytes()).isNotEqualTo(baselineFileContents)
        val result4 = project.executor().run(":app:updateLintBaseline")
        GradleTaskSubject.assertThat(result4.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        GradleTaskSubject.assertThat(result4.getTask(":app:updateLintBaselineDebug")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when a user runs updateLintBaseline and lint at the same time.
        val result5 = project.executor().run("updateLintBaseline", "lint")
        ScannerSubject.assertThat(result5.stdout).doesNotContain("Gradle detected a problem")
        GradleTaskSubject.assertThat(result5.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        GradleTaskSubject.assertThat(result5.getTask(":app:updateLintBaselineDebug")).wasUpToDate()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is no baseline file specified.
        assertThat(baselineFile.delete()).isTrue()
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "baseline = file('lint-baseline.xml')",
            ""
        )
        val result6 = project.executor().run(":app:updateLintBaseline")
        GradleTaskSubject.assertThat(result6.getTask(":app:lintAnalyzeDebug")).wasUpToDate()
        GradleTaskSubject.assertThat(result6.getTask(":app:updateLintBaselineDebug")).didWork()
        PathSubject.assertThat(baselineFile).doesNotExist()
        ScannerSubject.assertThat(result6.stdout)
            .contains(
                """
                No baseline file is specified, so no baseline file will be created.

                Please specify a baseline file in the build.gradle file like so:

                ```
                android {
                    lint {
                        baseline = file("lint-baseline.xml")
                    }
                }
                ```
                """.trimIndent()
            )
    }

    @Test
    fun testMissingBaselineIsEmptyBaseline() {
        // Test that android.experimental.lint.missingBaselineIsEmptyBaseline has the desired
        // effects when running the updateLintBaseline and lint tasks.
        TestFileUtils.appendToFile(
            projectWithoutIssues.buildFile,
            """
                android {
                    lint {
                        disable 'MissingApplicationIcon'
                        baseline = file('lint-baseline.xml')
                    }
                }
            """.trimIndent()
        )

        // First run updateLintBaseline without the boolean flag and check that an empty baseline
        // file is written.
        val baselineFile = File(projectWithoutIssues.projectDir, "lint-baseline.xml")
        PathSubject.assertThat(baselineFile).doesNotExist()
        projectWithoutIssues.executor().run("updateLintBaseline")
        PathSubject.assertThat(baselineFile).exists()
        PathSubject.assertThat(baselineFile).doesNotContain("</issue>")

        // Then run updateLinBaseline with the boolean flag and check that the baseline file is
        // deleted.
        projectWithoutIssues.executor()
            .with(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE, true)
            .run("updateLintBaseline")
        PathSubject.assertThat(baselineFile).doesNotExist()

        // Then run lint twice with the boolean flag and check that the lint reporting task is
        // up-to-date the second time. Regression test for b/237813416.
        projectWithoutIssues.executor()
            .with(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE, true)
            .run("lint")
            .also { result ->
                GradleTaskSubject.assertThat(result.getTask(":lintReportDebug")).didWork()
            }
        PathSubject.assertThat(baselineFile).doesNotExist()
        projectWithoutIssues.executor()
            .with(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE, true)
            .run("lint")
            .also { result ->
                GradleTaskSubject.assertThat(result.getTask(":lintReportDebug")).wasUpToDate()
            }

        // Finally, run lint with the boolean flag and without a baseline file when there is an
        // issue, in which case the build should fail.
        TestFileUtils.searchAndReplace(projectWithoutIssues.buildFile, "disable", "error")
        val result = projectWithoutIssues.executor()
            .with(BooleanOption.MISSING_LINT_BASELINE_IS_EMPTY_BASELINE, true)
            .expectFailure()
            .run("lint")
        ScannerSubject.assertThat(result.stdout).contains("MissingApplicationIcon")
    }
}
