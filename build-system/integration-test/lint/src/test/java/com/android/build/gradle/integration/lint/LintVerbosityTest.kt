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
 * Integration test testing lint verbosity.
 */
class LintVerbosityTest {

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
                                    error 'AccidentalOctal'
                                }
                                defaultConfig {
                                    versionCode 010
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    @Test
    fun testQuiet() {
        // first check that we see "Wrote HTML ..." in stdout if running with --info and quiet=false
        project.executor().withArgument("--info").run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).contains("Wrote HTML report to ")
        // then set quiet to true and check that stdout doesn't contain "Scanning".
        TestFileUtils.searchAndReplace(project.buildFile, "quiet false", "quiet true")
        project.executor().withArgument("--info").run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stdout).doesNotContain("Wrote HTML report to ")
    }

    // Regression test for b/187329866
    @Test
    fun testErrorMessage() {
        TestFileUtils.searchAndReplace(project.buildFile, "abortOnError false", "abortOnError true")
        project.executor().expectFailure().run("lintDebug")
        ScannerSubject.assertThat(project.buildResult.stderr)
            .contains("Lint found errors in the project; aborting build.")
    }
}
