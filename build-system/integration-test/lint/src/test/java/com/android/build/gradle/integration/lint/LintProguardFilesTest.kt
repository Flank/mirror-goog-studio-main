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
 * Integration test testing that lint reports issues with proguard files.
 *
 * Regression test for b/67156629
 */
class LintProguardFilesTest {

    @get:Rule
    val appProject: GradleTestProject =
        GradleTestProject.builder()
            .withName("appProject")
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .withFile("proguard-rules.pro", "foo.\ufeffbar")
                    .appendToBuild(
                        """
                            android {
                                buildTypes {
                                    release {
                                        minifyEnabled true
                                        proguardFiles 'proguard-rules.pro'
                                    }
                                }

                                lintOptions {
                                    abortOnError false
                                    textOutput file("lint-results.txt")
                                    error 'ByteOrderMark'
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    @get:Rule
    val libProject: GradleTestProject =
        GradleTestProject.builder()
            .withName("libProject")
            .fromTestApp(
                MinimalSubProject.lib("com.example.lib")
                    .withFile("consumer-rules.pro", "foo.\ufeffbar")
                    .appendToBuild(
                        """
                            android {
                                defaultConfig {
                                    consumerProguardFiles 'consumer-rules.pro'
                                }

                                lintOptions {
                                    abortOnError false
                                    textOutput file("lint-results.txt")
                                    error 'ByteOrderMark'
                                }
                            }
                        """.trimIndent()
                    )
            ).create()

    @Test
    fun testIssueFromProguardFile() {
        appProject.executor().run("lintRelease")
        assertThat(appProject.file("lint-results.txt")).contains(
            "proguard-rules.pro:1: Error: Found byte-order-mark in the middle of a file"
        )
    }

    @Test
    fun testIssueFromConsumerProguardFile() {
        libProject.executor().run("lint")
        assertThat(libProject.file("lint-results.txt")).contains(
            "consumer-rules.pro:1: Error: Found byte-order-mark in the middle of a file"
        )
    }
}
