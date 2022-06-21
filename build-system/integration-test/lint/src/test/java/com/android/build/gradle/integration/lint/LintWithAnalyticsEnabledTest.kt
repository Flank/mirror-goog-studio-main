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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Integration test that runs lint with analytics enabled. Regression test for b/178904638  */
class LintWithAnalyticsEnabledTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("lintKotlin")
            .create()

    @Before
    fun disableAbortOnError() {
        project.getSubproject(":app").buildFile
            .appendText("\nandroid.lintOptions.abortOnError=false\n")
        project.getSubproject(":library").buildFile
            .appendText("\nandroid.lintOptions.abortOnError=false\n")
    }

    @Test
    fun testLint() {
        getExecutor().run("lint")
    }

    @Test
    fun testLintFix() {
        val result = getExecutor().expectFailure().run("lintFix")
        ScannerSubject.assertThat(result.stderr)
            .contains("Aborting build since sources were modified to apply quickfixes")
    }

    @Test
    fun testBuild() {
        getExecutor().run("build")
    }

    private fun getExecutor(): GradleTaskExecutor =
        project.executor().with(BooleanOption.ENABLE_PROFILE_JSON, true)
}
