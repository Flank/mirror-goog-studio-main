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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test for the standalone lint plugin.
 *
 * <p>Tip: To execute just this test run:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:lint:test --tests=LintStandaloneVitalTest
 * </pre>
 */
class LintStandaloneVitalTest {

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestProject("lintStandaloneVital").create()

    @Test
    fun checkStandaloneLintVital() {
        // Run twice to catch issues with configuration caching
        project.executor().expectFailure().run(":cleanLintVital", "lintVital")
        val result = project.executor().expectFailure().run(":cleanLintVital", "lintVital")

        result.stderr.use {
            assertThat(it).contains(
                """
                    Lint found errors in the project; aborting build.

                    Fix the issues identified by lint, or add the following to your build script to proceed with errors:
                    ...
                    lintOptions {
                        abortOnError false
                    }
                    ...
                """.trimIndent()
            )
        }

        result.stderr.use {
            assertThat(it).contains("MyClass.java:5: Error: Use Boolean.valueOf(true) instead")
            assertThat(it).contains("1 errors, 0 warnings")
        }
    }
}
