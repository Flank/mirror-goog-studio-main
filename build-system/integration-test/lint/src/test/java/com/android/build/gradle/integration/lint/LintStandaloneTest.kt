/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for the standalone lint plugin.
 *
 * <p>Tip: To execute just this test run:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:lint:test --tests=LintStandaloneTest
 * </pre>
 */
@RunWith(FilterableParameterized::class)
class LintStandaloneTest(private val runLintInProcess: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "runLintInProcess = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestProject("lintStandalone").create()

    @Test
    fun checkStandaloneLint() {
        getExecutor().run( ":lint")

        val file = project.file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(file).contains("build.gradle:7: Warning: no Java language level directives")
        assertThat(file).contains("0 errors, 2 warnings")

        // Check that lint copies the result to the new location if that is changed
        // But the analysis itself is not re-run in the new integration
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "textOutput file(\"lint-results.txt\")",
            "textOutput file(\"lint-results2.txt\")"
        )
        // Run twice to catch issues with configuration caching
        getExecutor().run(":lint")
        getExecutor().run(":lint")
        val secondFile = project.file("lint-results2.txt")
        assertThat(secondFile).exists()
        assertThat(secondFile).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(secondFile).contains("build.gradle:7: Warning: no Java language level directives")
        assertThat(secondFile).contains("0 errors, 2 warnings")
    }

    @Test
    fun checkStandaloneLintFailure()  {
        TestFileUtils.appendToFile(
            project.buildFile,
            "\n\nlintOptions.error 'UseValueOf'\n\n"
        )
        getExecutor().expectFailure().run( ":lint")
        ScannerSubject.assertThat(project.buildResult.stderr)
            .contains("Lint found errors in the project; aborting build.")
        assertThat(project.buildResult.failedTasks).contains(":lint")
        assertThat(project.buildResult.didWorkTasks).contains(":lintReport")
        assertThat(project.buildResult.failedTasks).doesNotContain(":lintReport")
    }

    @Test
    fun checkOutputsNotOverlapping() {
        val lintModelDir =
            FileUtils.join(project.intermediatesDir, "lintReport", "android-lint-model")
        val lintFixModelDir =
            FileUtils.join(project.intermediatesDir, "lintFix", "android-lint-model")
        val lintVitalModelDir =
            FileUtils.join(project.intermediatesDir, "lintVitalReport", "android-lint-model")

        getExecutor().run(":lint")
        assertThat(lintModelDir).exists()
        assertThat(lintFixModelDir).doesNotExist()
        assertThat(lintVitalModelDir).doesNotExist()

        getExecutor().expectFailure().run(":cleanLintReport", ":lintFix")
        assertThat(lintModelDir).doesNotExist()
        assertThat(lintFixModelDir).exists()
        assertThat(lintVitalModelDir).doesNotExist()

        getExecutor().run(":cleanLintFix", ":lintVital")
        assertThat(lintModelDir).doesNotExist()
        assertThat(lintFixModelDir).doesNotExist()
        assertThat(lintVitalModelDir).exists()
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.RUN_LINT_IN_PROCESS, runLintInProcess)
}
