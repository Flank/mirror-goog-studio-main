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
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LintGlobalRuleJarsTest {

    @get:Rule
    val project: GradleTestProject =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create()

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `Jars set via environment variable affect up-to-date checking`() {
        val lintJar = temporaryFolder.newFolder().resolve("abcdefg.jar")

        val absolutePath = lintJar.absolutePath
        assertThat(absolutePath).isNotEmpty()
        val executor =
                project.executor()
                        .withEnvironmentVariables(mapOf("ANDROID_LINT_JARS" to absolutePath))

        val lintTaskName = ":lintDebug"
        val lintReportTaskName = ":lintReportDebug"
        val lintAnalyzeTaskName = ":lintAnalyzeDebug"
        executor.run(lintTaskName)
        executor.run(lintTaskName).also { result ->
            assertThat(result.getTask(lintReportTaskName)).wasUpToDate()
            assertThat(result.getTask(lintAnalyzeTaskName)).wasUpToDate()
        }

        FileUtils.createFile(lintJar, "FOO_BAR")
        executor.run(lintTaskName).also { result ->
            assertThat(result.getTask(lintReportTaskName)).didWork()
            assertThat(result.getTask(lintAnalyzeTaskName)).didWork()
            assertThat(result.stdout).doesNotContain("this will stop working soon.")
        }
    }
}
