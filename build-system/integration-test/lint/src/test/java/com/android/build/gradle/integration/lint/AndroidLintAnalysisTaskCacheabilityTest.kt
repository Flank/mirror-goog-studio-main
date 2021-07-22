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
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * This test checks that the [AndroidLintAnalysisTask]'s outputs are not affected by properties
 * which aren't annotated as inputs.
 */
class AndroidLintAnalysisTaskCacheabilityTest {

    @get:Rule
    val project1: GradleTestProject =
        GradleTestProject.builder().withName("project1").fromTestProject("lintKotlin").create()

    @get:Rule
    val project2: GradleTestProject =
        GradleTestProject.builder().withName("project2").fromTestProject("lintKotlin").create()

    @Before
    fun before() {
        listOf(project1, project2).forEach {
            it.getSubproject(":app")
                .buildFile
                .appendText(
                    """
                        android {
                            lint {
                                abortOnError = false
                                checkDependencies = true
                                xmlOutput = file("lint-report.xml")
                            }
                        }
                    """.trimIndent()
                )
        }
    }

    @Test
    fun testDifferentProjectsProduceSameAnalysisOutputs() {
        project1.execute(":app:clean", ":app:lintDebug")
        project2.execute(":app:clean", ":app:lintDebug")

        // Assert that the differences between the projects cause differences in the projects' lint
        // reports.
        val lintReport1 = project1.file("app/lint-report.xml")
        val lintReport2 = project2.file("app/lint-report.xml")
        assertThat(lintReport1).contains("project1")
        assertThat(lintReport1).doesNotContain("project2")
        assertThat(lintReport2).contains("project2")
        assertThat(lintReport2).doesNotContain("project1")

        // Assert that lint analysis outputs are identical
        val appPartialResultsDir1 =
            FileUtils.join(
                project1.getSubproject(":app")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "out"
            )
        val libPartialResultsDir1 =
            FileUtils.join(
                project1.getSubproject(":library")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "out"
            )
        val appPartialResultsDir2 =
            FileUtils.join(
                project2.getSubproject(":app")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "out"
            )
        val libPartialResultsDir2 =
            FileUtils.join(
                project2.getSubproject(":library")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "out"
            )
        listOf(appPartialResultsDir1, appPartialResultsDir2).forEach {
            assertThat(it.listFiles()?.asList())
                .containsExactlyElementsIn(
                    listOf(
                        File(it, "lint-definite-debug.xml"),
                        File(it, "lint-issues-debug.xml"),
                        File(it, "lint-partial-debug.xml")
                    )
                )
        }
        listOf(libPartialResultsDir1, libPartialResultsDir2).forEach {
            assertThat(it.listFiles()?.asList())
                .containsExactlyElementsIn(
                    listOf(
                        File(it, "lint-definite-debug.xml"),
                        File(it, "lint-partial-debug.xml")
                    )
                )
        }
        assertThat(File(appPartialResultsDir1, "lint-definite-debug.xml").readText())
            .isEqualTo(File(appPartialResultsDir2, "lint-definite-debug.xml").readText())
        assertThat(File(appPartialResultsDir1, "lint-issues-debug.xml").readText())
            .isEqualTo(File(appPartialResultsDir2, "lint-issues-debug.xml").readText())
        assertThat(File(appPartialResultsDir1, "lint-partial-debug.xml").readText())
            .isEqualTo(File(appPartialResultsDir2, "lint-partial-debug.xml").readText())
        assertThat(File(libPartialResultsDir1, "lint-definite-debug.xml").readText())
            .isEqualTo(File(libPartialResultsDir2, "lint-definite-debug.xml").readText())
        assertThat(File(libPartialResultsDir1, "lint-partial-debug.xml").readText())
            .isEqualTo(File(libPartialResultsDir2, "lint-partial-debug.xml").readText())
    }
}
