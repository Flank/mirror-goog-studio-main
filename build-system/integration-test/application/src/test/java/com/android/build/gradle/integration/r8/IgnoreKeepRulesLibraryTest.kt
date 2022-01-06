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

package com.android.build.gradle.integration.r8

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithTextEntries
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IgnoreKeepRulesLibraryTest {

    @get:Rule
    val lib = GradleTestProject.builder()
            .withAdditionalMavenRepo(mavenRepo)
            .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(lib.buildFile,
                """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                        }
                    }

                    flavorDimensions 'version'
                    productFlavors {
                        paid {}
                    }
                }

                dependencies {
                    implementation '$LIB_FOO_ID'
                    implementation '$LIB_BAR_ID'
                }
            """.trimIndent()
        )
    }

    @Test
    fun testBasicMergingAndFiltering() {
        lib.executor().run(":minifyPaidDebugWithR8")
        var configuration = lib.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).contains(LIB_FOO_RULE)
        assertThat(configuration).contains(LIB_BAR_RULE)

        ignoreProguardArtifact(BUILD_TYPES, "debug", LIB_BAR_ID)
        ignoreProguardArtifact(PRODUCT_FLAVORS, "paid", LIB_FOO_ID)

        lib.executor().run(":minifyPaidDebugWithR8")
        configuration = lib.getOutputFile("mapping", "paidDebug", "configuration.txt")

        assertThat(configuration).doesNotContain(LIB_BAR_RULE)
        assertThat(configuration).doesNotContain(LIB_FOO_RULE)
    }

    @Test
    fun testIgnoreAllKeepRules() {
        lib.executor().run(":minifyPaidDebugWithR8")
        var configuration = lib.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).contains(LIB_FOO_RULE)
        assertThat(configuration).contains(LIB_BAR_RULE)

        TestFileUtils.appendToFile(
                lib.buildFile,
                """
                    android {
                        buildTypes {
                            debug {
                                optimization {
                                    keepRules { ignoreAllExternalDependencies true }
                                }
                            }
                        }
                    }
                """.trimIndent()
        )

        lib.executor().run(":minifyPaidDebugWithR8")
        configuration = lib.getOutputFile("mapping", "paidDebug", "configuration.txt")

        assertThat(configuration).doesNotContain(LIB_BAR_RULE)
        assertThat(configuration).doesNotContain(LIB_FOO_RULE)
    }

    @Test
    fun testVersionWildcardMatching() {
        lib.executor().run(":minifyPaidDebugWithR8")
        var configuration = lib.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).contains(LIB_FOO_RULE)
        assertThat(configuration).contains(LIB_BAR_RULE)

        TestFileUtils.appendToFile(
                lib.buildFile,
                """
                    android {
                        buildTypes {
                            debug {
                                optimization {
                                    keepRules {
                                        ignoreExternalDependencies '$LIB_FOO_ID_NO_VERSION'
                                        ignoreExternalDependencies '$LIB_BAR_ID_UNSUPPORTED_FORMAT'
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
        )

        val result = lib.executor().run(":minifyPaidDebugWithR8")
        configuration = lib.getOutputFile("mapping", "paidDebug", "configuration.txt")

        assertThat(configuration).contains(LIB_BAR_RULE)
        assertThat(configuration).doesNotContain(LIB_FOO_RULE)
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                    "Keep rules from [$LIB_BAR_ID_UNSUPPORTED_FORMAT] are specified to be ignored")
        }
    }

    private fun ignoreProguardArtifact(
            variantDimensions: String,
            variantDimension: String,
            id: String
    ) {
        TestFileUtils.appendToFile(
                lib.buildFile,
                """
                android {
                    $variantDimensions {
                        $variantDimension {
                            optimization {
                                keepRules {
                                    ignoreExternalDependencies "$id"
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    private val mavenRepo : MavenRepoGenerator
        get() = MavenRepoGenerator(
                listOf(
                        MavenRepoGenerator.Library(
                                LIB_FOO_ID,
                                jarWithTextEntries(PROGUARD_PATH to LIB_FOO_RULE)
                        ),
                        MavenRepoGenerator.Library(
                                LIB_BAR_ID,
                                jarWithTextEntries(PROGUARD_PATH to LIB_BAR_RULE)
                        )
                )
        )

    companion object {
        private const val BUILD_TYPES = "buildTypes"
        private const val PRODUCT_FLAVORS = "productFlavors"
        private const val LIB_FOO_ID = "com.example:foo:1.0.0"
        private const val LIB_FOO_ID_NO_VERSION = "com.example:foo"
        private const val LIB_BAR_ID = "com.example:bar:1.0.0"
        private const val LIB_BAR_ID_UNSUPPORTED_FORMAT = "com.example:bar:"
        private const val LIB_FOO_RULE = "-keep class foo { *; }"
        private const val LIB_BAR_RULE = "-keep class bar { *; }"
        private const val PROGUARD_PATH = "META-INF/proguard/rules.txt"
    }
}
