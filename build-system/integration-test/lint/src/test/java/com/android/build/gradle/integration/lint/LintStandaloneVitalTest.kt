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
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for the standalone lint plugin.
 *
 *
 * To run just this test:
 * ./gradlew :base:build-system:integration-test:application:test -D:base:build-system:integration-test:application:test.single=LintStandaloneVitalTest
 */
@RunWith(FilterableParameterized::class)
class LintStandaloneVitalTest(private val lintInvocationType: LintInvocationType) {

    companion object {
        @get:JvmStatic
        @get:Parameterized.Parameters(name = "{0}")
        val params get() = LintInvocationType.values()
    }

    @Rule
    @JvmField
    var project = lintInvocationType
        .testProjectBuilder()
        .fromTestProject("lintStandaloneVital")
        .create()

    @Test
    fun checkStandaloneLintVital() {
        // Run twice to catch issues with configuration caching
        project.executor().expectFailure().run(":cleanLintVital", "lintVital")
        val result = project.executor().expectFailure().run(":cleanLintVital", "lintVital")

        result.stderr.use {
            assertThat(it).contains(
                "" +
                        "Lint found errors in the project; aborting build.\n" +
                        "  \n" +
                        "  Fix the issues identified by lint, or add the following to your build script to proceed with errors:\n" +
                        "  ...\n" +
                        "  lintOptions {\n" +
                        "      abortOnError false\n" +
                        "  }\n" +
                        "  ..."
            )
        }

        if (lintInvocationType == LintInvocationType.NEW_LINT_MODEL) {
            result.stderr.use {
                assertThat(it).contains("MyClass.java:5: Error: Use Boolean.valueOf(true) instead")
                assertThat(it).contains("1 errors, 0 warnings")
            }
        } else {
            val file = project.file("lint-results.txt")
            assertThat(file).exists()
            assertThat(file).contains("MyClass.java:5: Error: Use Boolean.valueOf(true) instead")
            assertThat(file).contains("1 errors, 0 warnings")
        }
    }
}
