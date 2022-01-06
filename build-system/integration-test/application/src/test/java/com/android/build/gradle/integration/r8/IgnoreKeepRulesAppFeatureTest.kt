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
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class IgnoreKeepRulesAppFeatureTest {

    private lateinit var app: GradleTestProject
    private lateinit var feature: GradleTestProject

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
            .withAdditionalMavenRepo(mavenRepo)
            .fromTestProject("dynamicApp")
            .create()

    @Before
    fun setUp() {
        app = project.getSubproject("app")
        feature = project.getSubproject("feature1")

        TestFileUtils.appendToFile(app.buildFile,
            """
                android {
                    defaultConfig {
                        minSdkVersion = 28
                    }

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
            """.trimIndent()
        )
    }

    @Test
    fun testBasicMergingAndFiltering() {
        addDependencies(app.buildFile, LIB_FOO_ID, LIB_BAR_ID)

        app.executor().run("minifyPaidDebugWithR8")
        var configuration = app.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).contains(LIB_BAR_RULE)
        assertThat(configuration).contains(LIB_FOO_RULE)

        ignoreProguardArtifact(app, BUILD_TYPES, "debug", LIB_BAR_ID)
        ignoreProguardArtifact(app, PRODUCT_FLAVORS, "paid", LIB_FOO_ID)

        app.executor().run("minifyPaidDebugWithR8")
        configuration = app.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).doesNotContain(LIB_BAR_RULE)
        assertThat(configuration).doesNotContain(LIB_FOO_RULE)
    }

    @Test
    fun testKeepRuleLibrariesVersionMismatch() {
        addDependencies(app.buildFile, LIB_FOO_ID)

        ignoreProguardArtifact(app, BUILD_TYPES, "debug", MISS_MATCHED_LIB_FOO_ID)

        val result = app.executor().run("minifyPaidDebugWithR8")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Keep rules from [$MISS_MATCHED_LIB_FOO_ID] are specified to be ignored")
        }
    }

    @Test
    fun testIgnoreKeepRulesFromDynamicFeature() {
        addDependencies(feature.buildFile, LIB_FOO_ID)

        feature.executor().run("assembleDebug")
        var configuration = app.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).contains(LIB_FOO_RULE)

        // Add specifications in app module to ignore keep rules from libraries added in
        // dynamic module
        ignoreProguardArtifact(app, BUILD_TYPES, "debug", LIB_FOO_ID)

        feature.executor().run("assembleDebug")
        configuration = app.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).doesNotContain(LIB_FOO_RULE)
    }

    @Test
    fun testIgnoreAllKeepRules() {
        addDependencies(app.buildFile, LIB_FOO_ID, LIB_BAR_ID)

        app.executor().run("minifyPaidDebugWithR8")
        var configuration = app.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).contains(LIB_BAR_RULE)
        assertThat(configuration).contains(LIB_FOO_RULE)

        TestFileUtils.appendToFile(
                app.buildFile,
                """
                    android {
                        productFlavors {
                            paid {
                                optimization {
                                    keepRules { ignoreAllExternalDependencies true }
                                }
                            }
                        }
                    }
                """.trimIndent()
        )

        app.executor().run("minifyPaidDebugWithR8")
        configuration = app.getOutputFile("mapping", "paidDebug", "configuration.txt")
        assertThat(configuration).doesNotContain(LIB_BAR_RULE)
        assertThat(configuration).doesNotContain(LIB_FOO_RULE)
    }

    private fun ignoreProguardArtifact(
        project: GradleTestProject,
        variantDimensions: String,
        variantDimension: String,
        id: String
    ) {
        TestFileUtils.appendToFile(
            project.buildFile,
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

    private fun addDependencies(buildFile: File, vararg dependencies: String) {
        for (dependency in dependencies) {
            TestFileUtils.appendToFile(
                    buildFile,
                    """
                        dependencies {
                            implementation '$dependency'
                        }
                    """.trimIndent()
            )
        }
    }

    private val mavenRepo : MavenRepoGenerator
        get() = MavenRepoGenerator(
                listOf(
                        MavenRepoGenerator.Library(
                                LIB_FOO_ID,
                                TestInputsGenerator.jarWithTextEntries(PROGUARD_PATH to LIB_FOO_RULE)
                        ),
                        MavenRepoGenerator.Library(
                                LIB_BAR_ID,
                                TestInputsGenerator.jarWithTextEntries(PROGUARD_PATH to LIB_BAR_RULE)
                        )
                )
        )

    companion object {
        private const val BUILD_TYPES = "buildTypes"
        private const val PRODUCT_FLAVORS = "productFlavors"
        private const val LIB_FOO_ID = "com.example:foo:1.0.0"
        private const val LIB_BAR_ID = "com.example:bar:1.0.0"
        private const val MISS_MATCHED_LIB_FOO_ID = "com.example:foo:2.0.0"
        private const val LIB_FOO_RULE = "-keep class foo { *; }"
        private const val LIB_BAR_RULE = "-keep class bar { *; }"
        private const val PROGUARD_PATH = "META-INF/proguard/rules.txt"
    }
}
