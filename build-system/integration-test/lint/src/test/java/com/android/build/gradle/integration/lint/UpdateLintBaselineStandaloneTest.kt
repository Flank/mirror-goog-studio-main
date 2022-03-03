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
import com.android.build.gradle.integration.common.truth.GradleTaskSubject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test for updating lint baselines with the standalone plugin using the updateLintBaseline task.
 */
class UpdateLintBaselineStandaloneTest {

    @get:Rule
    val project = builder().fromTestProject("lintStandalone").create()

    @Test
    fun checkUpdateLintBaseline() {
        // This test runs updateLintBaseline in 6 scenarios:
        //   (1)  when there is no existing baseline,
        //   (2)  when the task is UP-TO-DATE,
        //   (3)  when there is already a correct existing baseline,
        //   (4)  when there is already an incorrect existing baseline,
        //   (5)  when a user runs updateLintBase and lint at the same time.
        //   (6)  when there is no baseline file specified.

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                lint {
                   baseline = file('lint-baseline.xml')
                }
            """.trimIndent()
        )

        // First test the case when there is no existing baseline.
        val baselineFile = File(project.projectDir, "lint-baseline.xml")
        PathSubject.assertThat(baselineFile).doesNotExist()
        val result1 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result1.getTask(":lintAnalyze")).didWork()
        GradleTaskSubject.assertThat(result1.getTask(":updateLintBaseline")).didWork()
        PathSubject.assertThat(baselineFile).exists()

        val baselineFileContents = baselineFile.readBytes()

        // Then test the case when the task is UP-TO-DATE.
        val result2 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result2.getTask(":lintAnalyze")).wasUpToDate()
        GradleTaskSubject.assertThat(result2.getTask(":updateLintBaseline")).wasUpToDate()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is already a correct existing baseline.
        project.executor().run("clean")
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)
        val result3 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result3.getTask(":lintAnalyze")).didWork()
        GradleTaskSubject.assertThat(result3.getTask(":updateLintBaseline")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is already an incorrect existing baseline.
        baselineFile.writeText("invalid")
        assertThat(baselineFile.readBytes()).isNotEqualTo(baselineFileContents)
        val result4 = project.executor().run("updateLintBaseline")
        GradleTaskSubject.assertThat(result4.getTask(":lintAnalyze")).wasUpToDate()
        GradleTaskSubject.assertThat(result4.getTask(":updateLintBaseline")).didWork()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when a user runs updateLintBaseline and lint at the same time.
        val result5 = project.executor().run("updateLintBaseline", "lint")
        ScannerSubject.assertThat(result5.stdout).doesNotContain("Gradle detected a problem")
        GradleTaskSubject.assertThat(result5.getTask(":lintAnalyze")).wasUpToDate()
        GradleTaskSubject.assertThat(result5.getTask(":updateLintBaseline")).wasUpToDate()
        PathSubject.assertThat(baselineFile).exists()
        assertThat(baselineFile.readBytes()).isEqualTo(baselineFileContents)

        // Then test the case when there is no baseline file specified.
        assertThat(baselineFile.delete()).isTrue()
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "baseline = file('lint-baseline.xml')",
            ""
        )
        val result6 = project.executor().run(":updateLintBaseline")
        GradleTaskSubject.assertThat(result6.getTask(":lintAnalyze")).wasUpToDate()
        GradleTaskSubject.assertThat(result6.getTask(":updateLintBaseline")).didWork()
        PathSubject.assertThat(baselineFile).doesNotExist()
        ScannerSubject.assertThat(result6.stdout)
            .contains(
                """
                No baseline file is specified, so no baseline file will be created.

                Please specify a baseline file in the build.gradle file like so:

                ```
                lint {
                    baseline = file("lint-baseline.xml")
                }
                ```
                """.trimIndent()
            )
    }
}
