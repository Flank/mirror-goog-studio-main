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
                                    error 'Fake'
                                    textReport true
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
}
