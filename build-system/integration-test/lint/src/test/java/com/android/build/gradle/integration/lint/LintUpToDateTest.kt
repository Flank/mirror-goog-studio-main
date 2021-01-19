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
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Integration test that lint can be up-to-date  */
class LintUpToDateTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withConfigurationCacheMaxProblems(4)
            .fromTestProject("lintKotlin")
            .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun disableAbortOnError() {
        project.getSubproject(":app").buildFile
            .appendText("\nandroid.lintOptions.abortOnError=false\n")
        project.executor().run(":app:cleanLintDebug")
    }

    @Test
    fun checkLintModels() {
        val firstRun = project.executor().run(":app:lintDebug")

        assertThat(firstRun.getTask(":app:lintDebug")).didWork()
        val lintResults = project.file("app/build/reports/lint-results.txt")
        assertThat(lintResults).contains("8 errors, 6 warnings")

        val secondRun = project.executor().run(":app:lintDebug")
        assertThat(secondRun.getTask(":app:lintDebug")).wasUpToDate()
    }

}
