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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LintApiDatabaseTest {

    @get:Rule
    val project: GradleTestProject =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val lintTaskName = ":lintDebug"
    private val lintReportTaskName = ":lintReportDebug"
    private val lintAnalyzeTaskName = ":lintAnalyzeDebug"

    // Test that specifying a lint API database via a system property affects lint task UP-TO-DATE
    // checking as expected.
    @Test
    fun testLintApiDatabaseFromSystemProperty() {
        val lintApiDatabase1 =
            temporaryFolder.newFolder().resolve("fake").also { it.writeText("foo") }
        val lintApiDatabase2 =
            temporaryFolder.newFolder().resolve("fake").also { it.writeText("foo") }
        val nonexistentFile =
            temporaryFolder.newFolder()
                .resolve("nonexistent")
                .also { assertThat(it).doesNotExist() }

        // Use a nonexistent lint api database file initially as a check that the build doesn't fail
        // in this case.
        project.executor().withArgument("-DLINT_API_DATABASE=${nonexistentFile.absolutePath}")
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).didWork()
                assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            }
        // lint tasks should run again if we specify a LINT_API_DATABASE system property.
        project.executor().withArgument("-DLINT_API_DATABASE=${lintApiDatabase1.absolutePath}")
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).didWork()
                assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            }
        // lint tasks should be up-to-date if we set a different lint api database file with the
        // same contents
        project.executor().withArgument("-DLINT_API_DATABASE=${lintApiDatabase2.absolutePath}")
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).wasUpToDate()
                assertThat(result.getTask(lintAnalyzeTaskName)).wasUpToDate()
            }
        // lint tasks should run again if we modify the contents of the lint api database file.
        lintApiDatabase2.appendText("bar")
        project.executor().withArgument("-DLINT_API_DATABASE=${lintApiDatabase2.absolutePath}")
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).didWork()
                assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            }
    }

    // Test that specifying a lint API database via an environment variable affects lint task
    // UP-TO-DATE checking as expected.
    @Test
    fun testLintApiDatabaseFromEnvironmentVariable() {
        val lintApiDatabase1 =
            temporaryFolder.newFolder().resolve("fake").also { it.writeText("foo") }
        val lintApiDatabase2 =
            temporaryFolder.newFolder().resolve("fake").also { it.writeText("foo") }
        val nonexistentFile =
            temporaryFolder.newFolder()
                .resolve("nonexistent")
                .also { assertThat(it).doesNotExist() }

        // Use a nonexistent lint api database file initially as a check that the build doesn't fail
        // in this case.
        project.executor()
            .withEnvironmentVariables(mapOf("LINT_API_DATABASE" to nonexistentFile.absolutePath))
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).didWork()
                assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            }
        // lint tasks should run again if we specify a LINT_API_DATABASE environment variable.
        project.executor()
            .withEnvironmentVariables(mapOf("LINT_API_DATABASE" to lintApiDatabase1.absolutePath))
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).didWork()
                assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            }
        // lint tasks should be up-to-date if we set a different lint api database file with the
        // same contents
        project.executor()
            .withEnvironmentVariables(mapOf("LINT_API_DATABASE" to lintApiDatabase2.absolutePath))
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).wasUpToDate()
                assertThat(result.getTask(lintAnalyzeTaskName)).wasUpToDate()
            }
        // lint tasks should run again if we modify the contents of the lint api database file.
        lintApiDatabase2.appendText("bar")
        project.executor()
            .withEnvironmentVariables(mapOf("LINT_API_DATABASE" to lintApiDatabase2.absolutePath))
            .run(lintTaskName).also { result ->
                assertThat(result.getTask(lintReportTaskName)).didWork()
                assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            }
    }
}
