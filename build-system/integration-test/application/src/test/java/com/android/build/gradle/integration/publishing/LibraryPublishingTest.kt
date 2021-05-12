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

package com.android.build.gradle.integration.publishing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Integration test for publishing library projects. */
class LibraryPublishingTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(setUpTestProject())
        .create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, HelloWorldApp.forPlugin("com.android.application"))
            .subproject(LIBRARY_MODULE, MinimalSubProject.lib(LIBRARY_PACKAGE))
            .build()
    }

    private lateinit var app: GradleTestProject
    private lateinit var library: GradleTestProject

    @Before
    fun setUp() {
        app = project.getSubproject(APP_MODULE)
        library = project.getSubproject(LIBRARY_MODULE)

        TestFileUtils.appendToFile(
            app.buildFile,
            """
                repositories {
                    maven { url '../testrepo' }
                }

                dependencies {
                    implementation 'com.example.android:myLib:1.0'
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            library.buildFile,
            """
                apply plugin: 'maven-publish'

                afterEvaluate {
                    publishing {
                        repositories {
                            maven { url '../testrepo' }
                        }

                        publications {
                            release(MavenPublication) {
                                groupId = 'com.example.android'
                                artifactId = 'myLib'
                                version = '1.0'

                                from components.release
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testNoAutomaticComponentCreation() {
        var result = library.executor()
            .with(BooleanOption.DISABLE_AUTOMATIC_COMPONENT_CREATION, true)
            .expectFailure().run("help")
        assertThat(result.failureMessage).contains("" +
                "Could not get unknown property 'release' for SoftwareComponentInternal")

        // When new DSL is used, AGP won't create software components for each variant automatically.
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant("debug") {}
                    }
                }
            """.trimIndent()
        )

        result = library.executor()
            .expectFailure().run("help")
        assertThat(result.failureMessage).contains("" +
                "Could not get unknown property 'release' for SoftwareComponentInternal")
    }

    @Test
    fun testSingleVariantPublishing() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant("release")
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publishReleasePublicationToMavenRepository")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMigrationWithoutChanges() {
        library.execute("clean", "publishReleasePublicationToMavenRepository")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMigrationWarningMessage() {
        val result = library.executor().run("help")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains("""
                Software Components will not be created automatically for Maven publishing from
                Android Gradle Plugin 8.0.
            """.trimIndent())
        }
    }

    @Test
    fun testPassingWrongVariantName() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant("foo")
                    }
                }
            """.trimIndent()
        )
        val result = library.executor().expectFailure().run("help")
        assertThat(result.failureMessage).contains("" +
                "Could not get unknown property 'release' for SoftwareComponentInternal")
    }

    companion object {
        private const val APP_MODULE = ":app"
        private const val LIBRARY_MODULE = ":library"
        private const val LIBRARY_PACKAGE = "com.example.lib"
    }
}
