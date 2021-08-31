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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import org.junit.Rule
import org.junit.Test

class LintSystemPropertiesTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            // FIXME(b/195978143)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun checkLintNotUpToDate() {
        project.executor().run(":lintDebug").also { result ->
            assertThat(result.getTask(":lintAnalyzeDebug")).didWork()
            assertThat(result.getTask(":lintReportDebug")).didWork()
        }

        // check that the lint tasks are up-to-date if nothing changes
        project.executor().run(":lintDebug").also { result ->
            assertThat(result.getTask(":lintAnalyzeDebug")).wasUpToDate()
            assertThat(result.getTask(":lintReportDebug")).wasUpToDate()
        }

        val systemProperties =
            listOf(
                "android.lint.log-jar-problems",
                "LINT_API_DATABASE",
                "java.vendor",
                "java.version",
                "lint.configuration.override",
                "lint.nullness.ignore-deprecated",
                "lint.unused-resources.exclude-tests",
                "lint.unused-resources.include-tests"
            )

        for (systemProperty in systemProperties) {
            // check that the lint tasks are not up-to-date if we set the system property
            project.executor()
                .withArgument("-D$systemProperty=foo")
                .run(":lintDebug")
                .also { result ->
                    assertThat(result.getTask(":lintAnalyzeDebug"))
                        .named(":lintAnalyzeDebug with -D$systemProperty=foo")
                        .didWork()
                    assertThat(result.getTask(":lintReportDebug"))
                        .named(":lintReportDebug with -D$systemProperty=foo")
                        .didWork()
                }

            // run build without any system properties before testing the next one
            project.executor().run(":lintDebug")
        }

        val reportTaskSystemProperties =
            listOf(
                "lint.autofix",
                "lint.baselines.continue",
                "lint.html.prefs",
                "user.home"
            )

        for (systemProperty in reportTaskSystemProperties) {
            // check that the lint reporting task is not up-to-date if we set the system property
            // (the lint analysis task should be up-to-date)
            project.executor()
                .withArgument("-D$systemProperty=foo")
                .run(":lintDebug")
                .also { result ->
                    assertThat(result.getTask(":lintAnalyzeDebug"))
                        .named(":lintAnalyzeDebug with -D$systemProperty=foo")
                        .wasUpToDate()
                    assertThat(result.getTask(":lintReportDebug"))
                        .named(":lintReportDebug with -D$systemProperty=foo")
                        .didWork()
                }

            // run build without any system properties before testing the next one
            project.executor().run(":lintDebug")
        }
    }
}
