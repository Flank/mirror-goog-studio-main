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
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

class LintCacheTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("lintDeps").withHeap("1001M").create()

    /**
     * Regression test for b/188187060. This test checks that lint uses a subdirectory of the build
     * directory for the lint cache. Lint won't write to the lint cache in some cases if it already
     * has the info it needs in memory, so this test is in a class of its own and the project is
     * given a unique heap size of "1001M" to ensure the test uses a fresh gradle daemon without
     * the lint cache info already in memory.
     */
    @Test
    fun testLintCache() {
        project.execute("clean", ":app:lintAnalyzeDebug", ":javalib:lintAnalyze")
        assertThat(project.getSubproject("app").getIntermediateFile("lint-cache")).isDirectory()
        assertThat(project.getSubproject("javalib").getIntermediateFile("lint-cache")).isDirectory()
    }
}
