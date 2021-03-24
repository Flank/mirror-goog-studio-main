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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

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

    private val app =
        MinimalSubProject.app("com.example.test")
            .appendToBuild("android.dynamicFeatures = [':feature']")
    private val feature = MinimalSubProject.dynamicFeature("com.example.test")
    private val lib1 = MinimalSubProject.lib("com.example.lib1")
    private val lib2 = MinimalSubProject.lib("com.example.lib2")

    @get:Rule
    val projectWithLibs: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":feature", feature)
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .dependency(feature, app)
                    .dependency(feature, lib1)
                    .dependency("api", app, lib2)
                    .build()
            )
            .create()

    @Test
    fun testUnusedResourcesInFeatureModules() {
        project.getExecutor().run("clean", "lint")

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

    // TODO(b/183566683) Stop supporting running lint from feature modules
    @Test
    fun runLintFromDynamicFeatures() {
        // Run twice to catch issues with configuration caching
        project.getExecutor().run(":feature1:clean", ":feature1:lint")
        project.getExecutor().run(":feature1:clean", ":feature1:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        assertThat(project.file("feature1/lint-results.txt")).containsAllOf(
            "Should explicitly set android:allowBackup to true or false",
            "Hardcoded string \"Button\", should use @string resource"
        )

        project.getExecutor().run(":feature2:clean", ":feature2:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        assertThat(project.file("feature2/lint-results.txt")).containsAllOf(
            "Should explicitly set android:allowBackup to true or false",
            "Hardcoded string \"Button\", should use @string resource"
        )
    }

    // TODO(b/183566683) Stop supporting running lint from feature modules.
    // TODO(b/178810169) Running lint from an app module with checkDependencies true should also
    //  analyze all of the dynamic feature module dependencies.
    @Test
    fun runLintFromDynamicFeatureWithCheckDependencies() {
        // checkDependencies in a dynamic feature module has no effect; this test is mainly to check
        // that we're not breaking people who might have it set to true currently.
        TestFileUtils.appendToFile(
            projectWithLibs.getSubproject(":feature").buildFile,
            """
                android {
                    lintOptions {
                        checkDependencies true
                        abortOnError false
                        textOutput file("lint-results.txt")
                    }
                }
                """.trimIndent()
        )

        // Add hard-coded resource to each module
        FileUtils.writeToFile(
            File(projectWithLibs.getSubproject(":app").mainResDir, "layout/app_layout.xml"),
            layout_text
        )
        FileUtils.writeToFile(
            File(projectWithLibs.getSubproject(":feature").mainResDir, "layout/feature_layout.xml"),
            layout_text
        )
        FileUtils.writeToFile(
            File(projectWithLibs.getSubproject(":lib1").mainResDir, "layout/lib1_layout.xml"),
            layout_text
        )
        FileUtils.writeToFile(
            File(projectWithLibs.getSubproject(":lib2").mainResDir, "layout/lib2_layout.xml"),
            layout_text
        )

        projectWithLibs.getExecutor().run("clean", ":feature:lint")
        assertThat(projectWithLibs.buildResult.failedTasks).isEmpty()

        assertThat(projectWithLibs.file("feature/lint-results.txt")).contains(
            "feature_layout.xml:10: Warning: Hardcoded string"
        )
        assertThat(projectWithLibs.file("feature/lint-results.txt")).doesNotContain(
            "app_layout.xml:10: Warning: Hardcoded string"
        )
        assertThat(projectWithLibs.file("feature/lint-results.txt")).doesNotContain(
            "lib1_layout.xml:10: Warning: Hardcoded string"
        )
        assertThat(projectWithLibs.file("feature/lint-results.txt")).doesNotContain(
            "lib2_layout.xml:10: Warning: Hardcoded string"
        )
    }

    @Test
    fun runLintFromApp() {
        // Run twice to catch issues with configuration caching
        project.getExecutor().run(":app:clean", ":app:lint")
        project.getExecutor().run(":app:clean", ":app:lint")
        assertThat(project.buildResult.failedTasks).isEmpty()

        assertThat(project.file("app/lint-results.txt")).containsAllOf(
            "base_layout.xml:10: Warning: Hardcoded string",
            "feature_layout.xml:10: Warning: Hardcoded string",
            "feature2_layout.xml:10: Warning: Hardcoded string"
        )
    }

    @Test
    fun testLintUpToDate() {
        project.getExecutor().run(":app:lintDebug")
        project.getExecutor().run(":app:lintDebug")
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
        project.getExecutor().run(":app:lintDebug")
        TestFileUtils.searchAndReplace(
            project.file("feature2/src/main/res/layout/feature2_layout.xml"),
            "\"Button\"",
            "\"AAAAAAAAAAA\""
        )
        project.getExecutor().run(":app:lintDebug")
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
        project.getExecutor().run(":app:lintVitalRelease")
        project.getExecutor().run(":app:lintVitalRelease")

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

    private fun GradleTestProject.getExecutor(): GradleTaskExecutor =
        this.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}

private val layout_text =
    """
        <?xml version="1.0" encoding="utf-8"?>
        <android.support.constraint.ConstraintLayout xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_height="match_parent"
            xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent">

            <Button
                android:id="@+id/button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Button" />
        </android.support.constraint.ConstraintLayout>
        """.trimIndent()
