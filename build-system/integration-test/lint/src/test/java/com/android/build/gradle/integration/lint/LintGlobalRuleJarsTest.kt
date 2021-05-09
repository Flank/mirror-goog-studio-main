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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
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
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun `Jars in prefs directory affect up-to-date checking`() {
        val executor = project.getExecutor().withPerTestPrefsRoot().apply {
            // invoke a build to force initialization of preferencesRootDir
            run("tasks")
        }
        val prefsLintDir = FileUtils.join(executor.preferencesRootDir, ".android", "lint")
        FileUtils.cleanOutputDir(prefsLintDir)

        val lintDebugTaskName = ":lintDebug"
        executor.run(lintDebugTaskName)
        executor.run(lintDebugTaskName).also { result ->
            assertThat(result.getTask(lintDebugTaskName)).wasUpToDate()
        }

        FileUtils.createFile(prefsLintDir.resolve("abcdefg.jar"), "FOO_BAR")
        executor.run(lintDebugTaskName).also { result ->
            assertThat(result.getTask(lintDebugTaskName)).didWork()
        }
    }

    private fun GradleTestProject.getExecutor(): GradleTaskExecutor =
        this.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
