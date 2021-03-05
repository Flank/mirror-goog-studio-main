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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(FilterableParameterized::class)
class LintDynamicFeatureTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("dynamicApp").create()

    @Test
    fun testUnusedResourcesInFeatureModules() {
        // TODO (b/180672373) Support the unused resource detector with lint partial analysis.
        Assume.assumeFalse(usePartialAnalysis)
        getExecutor().run("clean", "lint")

        val file = project.file("app/lint-results.txt")
        assertThat(file).containsAllOf(
            "The resource R.string.unused_from_feature1 appears to be unused",
            "The resource R.string.unused_from_app appears to be unused"
        )

        assertThat(file).doesNotContain(
            "The resource R.string.used_from_app appears to be unused"
        )
        assertThat(file).doesNotContain(
            "The resource R.string.used_from_feature1 appears to be unused"
        )
        assertThat(file).doesNotContain(
            "The resource R.string.used_from_feature2 appears to be unused"
        )
    }

    @Test
    fun runLintFromDynamicFeatures() {
        // Run twice to catch issues with configuration caching
        getExecutor().run(":feature1:clean", ":feature1:lint")
        getExecutor().run(":feature1:clean", ":feature1:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        assertThat(project.file("feature1/lint-results.txt")).containsAllOf(
            "Should explicitly set android:allowBackup to true or false",
            "Hardcoded string \"Button\", should use @string resource"
        )

        getExecutor().run(":feature2:clean", ":feature2:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        assertThat(project.file("feature2/lint-results.txt")).containsAllOf(
            "Should explicitly set android:allowBackup to true or false",
            "Hardcoded string \"Button\", should use @string resource"
        )
    }

    @Test
    fun runLintFromApp() {
        // Run twice to catch issues with configuration caching
        getExecutor().run(":app:clean", ":app:lint")
        getExecutor().run(":app:clean", ":app:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        // TODO (b/180672373) Merge issues from dynamic features into app report when using lint
        //  partial analysis.
        if (!usePartialAnalysis) {
            assertThat(project.file("app/lint-results.txt")).containsAllOf(
                "base_layout.xml:10: Warning: Hardcoded string",
                "feature_layout.xml:10: Warning: Hardcoded string",
                "feature2_layout.xml:10: Warning: Hardcoded string"
            )
        }
    }

    @Test
    fun testLintUpToDate() {
        getExecutor().run(":app:lintDebug")
        getExecutor().run(":app:lintDebug")
        if (usePartialAnalysis) {
            assertThat(project.buildResult.upToDateTasks).containsAtLeastElementsIn(
                listOf(
                    ":app:lintDebug",
                    ":app:lintAnalyzeDebug",
                    ":feature1:lintAnalyzeDebug",
                    ":feature2:lintAnalyzeDebug",
                )
            )
        } else {
            // The lint task should not be up-to-date if not using partial analysis with dynamic
            // features because the inputs are not modeled correctly in that case.
            assertThat(project.buildResult.didWorkTasks).containsAtLeastElementsIn(
                listOf(":app:lintDebug")
            )
        }
    }

    @Test
    fun testLintWithIncrementalChanges() {
        getExecutor().run(":app:lintDebug")
        TestFileUtils.searchAndReplace(
            project.file("feature2/src/main/res/layout/feature2_layout.xml"),
            "\"Button\"",
            "\"AAAAAAAAAAA\""
        )
        getExecutor().run(":app:lintDebug")
        if (usePartialAnalysis) {
            assertThat(project.buildResult.upToDateTasks).containsAtLeastElementsIn(
                listOf(
                    ":app:lintAnalyzeDebug",
                    ":feature1:lintAnalyzeDebug",
                )
            )
            assertThat(project.buildResult.didWorkTasks).containsAtLeastElementsIn(
                listOf(
                    ":app:lintDebug",
                    ":feature2:lintAnalyzeDebug",
                )
            )
        } else {
            // The lint task should not be up-to-date if not using partial analysis with dynamic
            // features because the inputs are not modeled correctly in that case.
            assertThat(project.buildResult.didWorkTasks).containsAtLeastElementsIn(
                listOf(":app:lintDebug")
            )
        }
    }


    @Test
    fun testLintVital() {
        getExecutor().run(":app:lintVitalRelease")
        getExecutor().run(":app:lintVitalRelease")

        assertThat(project.buildResult.upToDateTasks).contains(":app:lintVitalRelease")

        // Dynamic Feature Modules are not analyzed by the main module's lintVital task
        assertThat(project.buildResult.tasks).doesNotContain(":feature1:lintVitalRelease")
        assertThat(project.buildResult.tasks).doesNotContain(":feature1:lintVitalAnalyzeRelease")
        assertThat(project.buildResult.tasks).doesNotContain(":feature2:lintVitalRelease")
        assertThat(project.buildResult.tasks).doesNotContain(":feature2:lintVitalAnalyzeRelease")

        if (usePartialAnalysis) {
            assertThat(project.buildResult.upToDateTasks).contains(":app:lintVitalAnalyzeRelease")
        }
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
