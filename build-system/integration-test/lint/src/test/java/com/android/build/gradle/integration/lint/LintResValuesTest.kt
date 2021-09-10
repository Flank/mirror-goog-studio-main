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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test testing that lint analyzes Android resources specified via resValue.
 */
class LintResValuesTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                        """
                            android {
                                defaultConfig {
                                    resValue "string", "foo", "foo"
                                }

                                lintOptions {
                                    abortOnError false
                                    textOutput file("lint-results.txt")
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    @Test
    fun testResValues() {
        project.executor().run("lintDebug")
        assertThat(project.file("lint-results.txt")).contains(
            "Warning: The resource R.string.foo appears to be unused [UnusedResources]"
        )
    }
}
