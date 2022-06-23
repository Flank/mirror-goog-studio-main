/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Integration test for the new lint models.  */
class LintModelIntegrationTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("lintKotlin")
            .dontOutputLogOnFailure()
            .create()

    @Test
    fun checkLintModels() {
        // Check lint runs correctly before asserting about the model.
        project.executor().expectFailure().run("clean", ":app:lintDebug")
        project.executor().expectFailure().run(":app:clean", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("10 errors, 4 warnings")

        checkLintModels(
            project = project,
            lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                .resolve("incremental/lintReportDebug"),
            modelSnapshotResourceRelativePath = "kotlinmodel/lintDebug",
            "debug-androidTestArtifact-dependencies.xml",
            "debug-androidTestArtifact-libraries.xml",
            "debug-mainArtifact-dependencies.xml",
            "debug-mainArtifact-libraries.xml",
            "debug-testArtifact-dependencies.xml",
            "debug-testArtifact-libraries.xml",
            "debug.xml",
            "module.xml",
        )
    }

    @Test
    fun checkLintModelsForShrinkable() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                        }
                    }
                }
            """.trimIndent()
        )
        // Check lint runs correctly before asserting about the model.
        project.executor().expectFailure().run(":app:clean", ":app:lintDebug")
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("10 errors, 4 warnings")

        val lintModelDir =
            project.getSubproject("app")
                .intermediatesDir.toPath()
                .resolve("incremental/lintReportDebug")
                .toFile()

        val projectModelFile = File(lintModelDir, "module.xml")
        assertThat(projectModelFile).isFile()
        assertThat(
            Files.readAllLines(projectModelFile.toPath())
                .map { applyReplacements(it, createReplacements(project)) }
                .none { it.contains("neverShrinking") }
        ).isTrue()

        val variantModelFile = File(lintModelDir, "debug.xml")
        assertThat(variantModelFile).isFile()
        assertThat(
            Files.readAllLines(variantModelFile.toPath())
                .map { applyReplacements(it, createReplacements(project)) }
                .any { it.contains("shrinking=\"true\"") }
        ).isTrue()
    }
}
