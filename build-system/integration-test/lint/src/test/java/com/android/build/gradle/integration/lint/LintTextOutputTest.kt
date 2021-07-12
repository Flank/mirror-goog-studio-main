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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test testing lintOptions.textOutput
 */
class LintTextOutputTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                        """
                            android {
                                lintOptions {
                                    disable 'AllowBackup', 'MissingApplicationIcon'
                                    error 'Fake'
                                    textOutput 'stdout'
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    // Regression test for b/192090767
    @Test
    fun testStdoutAndStderr() {
        // first check that we see expected output in stdout
        project.executor().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).contains("Unknown issue id \"Fake\"")
        ScannerSubject.assertThat(project.buildResult.stderr).doesNotContain(
            "Unknown issue id \"Fake\""
        )

        // then replace 'stdout' with 'stderr' and check that we see expected output in stderr
        TestFileUtils.searchAndReplace(project.buildFile, "stdout", "stderr")
        project.executor().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain(
            "Unknown issue id \"Fake\""
        )
        ScannerSubject.assertThat(project.buildResult.stderr).contains("Unknown issue id \"Fake\"")

        // finally replace 'stderr' with a file and check that the text report is not being printed
        // to stdout or stderr
        TestFileUtils.searchAndReplace(project.buildFile, "'stderr'", "file(\"lint-results.txt\")")
        project.executor().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain(
            "Unknown issue id \"Fake\""
        )
        ScannerSubject.assertThat(project.buildResult.stderr).doesNotContain(
            "Unknown issue id \"Fake\""
        )
    }

    // Regression test for b/191897708
    @Test
    fun testStdoutWhenUpToDate() {
        project.executor().run("lintDebug")
        project.executor().run("lintDebug")
        assertThat(project.buildResult.upToDateTasks).contains(":lintReportDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).contains("Unknown issue id \"Fake\"")
    }

    // Regression test for b/158259845
    @Test
    fun testNoIssuesFound() {
        // First modify the build file so there will be no lint issues.
        TestFileUtils.searchAndReplace(project.buildFile, "error 'Fake'", "// error 'Fake'")
        // Verify that stdout and stderr don't contain "No issues found"
        project.executor().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain("No issues found")
        ScannerSubject.assertThat(project.buildResult.stderr).doesNotContain("No issues found")
        // Verify that the text report *does* contain "No issues found" when written to a file
        TestFileUtils.searchAndReplace(project.buildFile, "'stdout'", "file(\"lint-results.txt\")")
        project.executor().run("lintDebug")
        PathSubject.assertThat(project.file("lint-results.txt")).contains("No issues found")
        // Now modify the build file so that there will be lint issues, and generate a lint baseline
        TestFileUtils.searchAndReplace(project.buildFile, "disable", "error")
        TestFileUtils.appendToFile(
            project.buildFile,
            "\n\nandroid.lintOptions.baseline file(\"lint-baseline.xml\")\n\n"
        )
        project.executor().expectFailure().run("lintDebug")
        // Verify that the text report says "0 errors, 0 warnings" when written to a file
        project.executor().run("lintDebug")
        PathSubject.assertThat(project.file("lint-results.txt")).contains("0 errors, 0 warnings")
        // Verify that we don't see "0 errors, 0 warnings" if we print to stdout
        TestFileUtils.searchAndReplace(project.buildFile, "file(\"lint-results.txt\")", "'stdout'")
        project.executor().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain("0 errors, 0 warnings")
        ScannerSubject.assertThat(project.buildResult.stderr).doesNotContain("0 errors, 0 warnings")
    }
}
