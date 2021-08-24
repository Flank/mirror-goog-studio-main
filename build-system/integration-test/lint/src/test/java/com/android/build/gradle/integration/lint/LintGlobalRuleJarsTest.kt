/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class LintGlobalRuleJarsTest(private val usePartialAnalysis: Boolean) {

    companion object {

        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject =
            GradleTestProject.builder()
                    // FIXME(b/195978143)
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `Jars in prefs directory affect up-to-date checking`() {
        // Use an isolated android_prefs_root folder so this test doesn't affect other lint tests.
        val executor = project.getExecutor().withPerTestPrefsRoot(true).apply {
            // invoke a build to force initialization of preferencesRootDir
            run("tasks")
        }
        val prefsLintDir = FileUtils.join(executor.preferencesRootDir, ".android", "lint")
        FileUtils.cleanOutputDir(prefsLintDir)

        val lintTaskName = ":lintDebug"
        val lintReportTaskName = ":lintReportDebug"
        val lintAnalyzeTaskName = ":lintAnalyzeDebug"
        executor.run(lintTaskName)
        executor.run(lintTaskName).also { result ->
            assertThat(result.getTask(lintReportTaskName)).wasUpToDate()
            if (usePartialAnalysis) assertThat(result.getTask(lintAnalyzeTaskName)).wasUpToDate()
        }

        FileUtils.createFile(prefsLintDir.resolve("abcdefg.jar"), "FOO_BAR")
        executor.run(lintTaskName).also { result ->
            assertThat(result.getTask(lintReportTaskName)).didWork()
            if (usePartialAnalysis) assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            assertThat(result.stdout).contains("(abcdefg.jar); this will stop working soon.")
        }

        // Now switch to the shared android_prefs_root folder to test we don't see the warning
        executor.withPerTestPrefsRoot(false).run(lintTaskName).also { result ->
            assertThat(result.stdout).doesNotContain("this will stop working soon.")
        }
    }

    @Test
    fun `Jars set via environment variable affect up-to-date checking`() {
        val lintJar = temporaryFolder.newFolder().resolve("abcdefg.jar")

        val absolutePath = lintJar.absolutePath
        assertThat(absolutePath).isNotEmpty()
        val executor =
                project.getExecutor()
                        .withEnvironmentVariables(mapOf("ANDROID_LINT_JARS" to absolutePath))

        val lintTaskName = ":lintDebug"
        val lintReportTaskName = ":lintReportDebug"
        val lintAnalyzeTaskName = ":lintAnalyzeDebug"
        executor.run(lintTaskName)
        executor.run(lintTaskName).also { result ->
            assertThat(result.getTask(lintReportTaskName)).wasUpToDate()
            if (usePartialAnalysis) assertThat(result.getTask(lintAnalyzeTaskName)).wasUpToDate()
        }

        FileUtils.createFile(lintJar, "FOO_BAR")
        executor.run(lintTaskName).also { result ->
            assertThat(result.getTask(lintReportTaskName)).didWork()
            if (usePartialAnalysis) assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            assertThat(result.stdout).doesNotContain("this will stop working soon.")
        }
    }

    private fun GradleTestProject.getExecutor(): GradleTaskExecutor =
            this.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
