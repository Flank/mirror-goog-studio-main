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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import org.junit.Rule
import org.junit.Test

class LintEnvironmentVariablesTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun checkLintNotUpToDate() {
        project.executor().run(":lintDebug").also { result ->
            assertThat(result.getTask(":lintAnalyzeDebug")).didWork()
            assertThat(result.getTask(":lintDebug")).didWork()
        }

        // check that the lint tasks are up-to-date if nothing changes
        project.executor().run(":lintDebug").also { result ->
            assertThat(result.getTask(":lintAnalyzeDebug")).wasUpToDate()
            assertThat(result.getTask(":lintDebug")).wasUpToDate()
        }

        // check that the lint tasks are not up-to-date if we set LINT_OVERRIDE_CONFIGURATION
        project.executor()
            .withEnvironmentVariables(mapOf("LINT_OVERRIDE_CONFIGURATION" to "foo"))
            .run(":lintDebug")
            .also { result ->
                assertThat(result.getTask(":lintAnalyzeDebug")).didWork()
                assertThat(result.getTask(":lintDebug")).didWork()
            }
    }
}
