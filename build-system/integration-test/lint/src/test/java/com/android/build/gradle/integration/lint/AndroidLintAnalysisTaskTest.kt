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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class AndroidLintAnalysisTaskTest {

    @get:Rule
    val lintKotlinProject: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("lintKotlin")
            .create()

    @Test
    fun testApp() {
        // Run twice to catch issues with configuration caching
        lintKotlinProject.execute(":app:clean", ":app:lintAnalyzeDebug")
        lintKotlinProject.execute(":app:clean", ":app:lintAnalyzeDebug")
        val partialResultsDir =
            FileUtils.join(
                lintKotlinProject.getSubproject(":app")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "out"
            )
        assertThat(partialResultsDir.listFiles()?.asList())
            .containsAtLeastElementsIn(
                listOf(
                    File(partialResultsDir, "lint-definite-debug.xml"),
                    File(partialResultsDir, "lint-issues-debug.xml"),
                    File(partialResultsDir, "lint-partial-debug.xml")
                )
            )
        assertThat(File(partialResultsDir, "lint-definite-debug.xml"))
            .contains("{:app*debug*sourceProvider*0*javaDir*1}")
    }

    @Test
    fun testAndroidLibrary() {
        // Run clean task and lint task separately, see https://github.com/gradle/gradle/issues/20897
        lintKotlinProject.execute(":library:clean")
        lintKotlinProject.execute(":library:lintAnalyzeDebug")
        // Run twice to catch issues with configuration caching
        lintKotlinProject.execute(":library:clean")
        lintKotlinProject.execute(":library:lintAnalyzeDebug")
        val partialResultsDir =
            FileUtils.join(
                lintKotlinProject.getSubproject(":library")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "out"
            )
        assertThat(partialResultsDir.listFiles()?.asList())
            .containsAtLeastElementsIn(
                listOf(
                    File(partialResultsDir, "lint-definite-debug.xml"),
                    File(partialResultsDir, "lint-partial-debug.xml")
                )
            )
        assertThat(File(partialResultsDir, "lint-definite-debug.xml"))
            .contains("{:library*debug*sourceProvider*0*resDir*0}")
    }
}
