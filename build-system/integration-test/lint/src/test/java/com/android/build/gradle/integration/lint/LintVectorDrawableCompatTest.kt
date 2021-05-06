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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration test for VectorDrawableCompatDetector.
 */
@RunWith(FilterableParameterized::class)
class LintVectorDrawableCompatTest(private val usePartialAnalysis: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                        """
                            android {
                                defaultConfig {
                                    vectorDrawables.useSupportLibrary false
                                }

                                lintOptions {
                                    abortOnError false
                                    textOutput file("lint-results.txt")
                                }
                            }

                            dependencies {
                                implementation 'com.android.support:appcompat-v7:$SUPPORT_LIB_VERSION'
                            }
                        """.trimIndent()
                    ).withFile(
                        "src/main/res/drawable/foo.xml",
                        """
                            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                                android:width="108dp"
                                android:height="108dp"
                                android:viewportWidth="108"
                                android:viewportHeight="108">

                                <path
                                    android:fillColor="#3DDC84"
                                    android:pathData="M0,0h108v108h-108z" />

                            </vector>
                        """.trimIndent()
                    ).withFile(
                        "src/main/res/layout/main_activity.xml",
                        """
                            <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                xmlns:app="http://schemas.android.com/apk/res-auto">

                                <ImageView app:srcCompat="@drawable/foo" />
                            </FrameLayout>
                        """.trimIndent()
                    )
            ).create()

    // Regression test for b/187341964
    @Test
    fun testVectorDrawableCompat() {
        project.getExecutor().run("lintDebug")
        assertThat(project.file("lint-results.txt")).exists()
        assertThat(project.file("lint-results.txt")).contains(
            "Error: To use VectorDrawableCompat, you need to set android.defaultConfig.vectorDrawables.useSupportLibrary = true"
        )
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "vectorDrawables.useSupportLibrary false",
            "vectorDrawables.useSupportLibrary true"
        )
        project.getExecutor().run("lintDebug")
        assertThat(project.file("lint-results.txt")).exists()
        assertThat(project.file("lint-results.txt")).doesNotContain(
            "Error: To use VectorDrawableCompat, you need to set android.defaultConfig.vectorDrawables.useSupportLibrary = true"
        )
    }

    private fun GradleTestProject.getExecutor(): GradleTaskExecutor =
        this.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
}
