/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for the standalone lint plugin.
 *
 * To run just this test:
 * ./gradlew :base:build-system:integration-test:application:test --tests LintStandaloneTest
 */
@RunWith(FilterableParameterized::class)
class LintStandaloneTest(lintInvocationType: LintInvocationType) {

    companion object {
        @get:JvmStatic
        @get:Parameterized.Parameters(name = "{0}")
        val params get() = LintInvocationType.values()
    }

    @Rule
    @JvmField
    var project = lintInvocationType
        .testProjectBuilder()
        .fromTestProject("lintStandalone")
        .create()

    @Test
    fun checkStandaloneLint() {
        project.executor().run(":cleanLint", ":lint")

        val file = project.file("lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(file).contains("build.gradle:7: Warning: no Java language level directives")
        assertThat(file).contains("0 errors, 2 warnings")

        // Check that lint re-runs if the options have changed.
        // Lint always re-runs at the moment, but we should fix this at some point. (b/117870210)
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "textOutput file(\"lint-results.txt\")",
            "textOutput file(\"lint-results2.txt\")"
        )
        // Run twice to catch issues with configuration caching
        project.executor().run(":cleanLint", ":lint")
        project.executor().run(":cleanLint", ":lint")

        val secondFile = project.file("lint-results2.txt")
        assertThat(secondFile).exists()
        assertThat(secondFile).contains("MyClass.java:5: Warning: Use Boolean.valueOf(true) instead")
        assertThat(secondFile).contains("build.gradle:7: Warning: no Java language level directives")
        assertThat(secondFile).contains("0 errors, 2 warnings")

    }
}
