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
import com.android.build.gradle.options.OptionalBooleanOption
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
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testNoAutomaticComponentCreation() {
        addPublication(RELEASE)
        var result = library.executor()
            .with(OptionalBooleanOption.DISABLE_AUTOMATIC_COMPONENT_CREATION, true)
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
        addPublication(RELEASE)
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
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMigrationWithoutChanges() {
        addPublication(RELEASE)
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMigrationWarningMessage() {
        addPublication(RELEASE)
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
        addPublication(RELEASE)
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

    @Test
    fun testBasicMultipleVariantPublishing() {
        addPublication(CUSTOM)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants("$CUSTOM") {
                            includeBuildTypeValues("debug", "release")
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMultipleVariantPublishingWithoutAttribute() {
        addPublication(CUSTOM)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants("$CUSTOM") {
                            includeBuildTypeValues("release")
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMultipleVariantPublishingWithFlavorAttribute() {
        addPublication(CUSTOM)
        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android {
                    flavorDimensions 'version'
                    productFlavors {
                        free {}
                        paid {}
                        internal {}
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    flavorDimensions 'version'
                    productFlavors {
                        free {}
                        paid {}
                        internal {}
                    }
                    publishing {
                        multipleVariants("$CUSTOM") {
                            includeBuildTypeValues("debug", "release")
                            includeFlavorDimensionAndValues("version", "free", "paid")
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        app.execute("clean", "assembleFreeDebug")

        val failure = app.executor().expectFailure().run("clean", "assembleInternalDebug")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains("Could not resolve com.example.android:myLib:1.0")
        }
    }

    @Test
    fun testMultipleVariantPublishingShortCut() {
        addPublication(CUSTOM)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants("custom") {
                            allVariants()
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMultipleVariantPublishingWithDefaultComponent() {
        addPublication(DEFAULT)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants {
                            allVariants()
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testMultipleVariantPublishingWithSameComponentName() {
        addPublication(CUSTOM)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants("custom") {
                            allVariants()
                        }
                        multipleVariants("custom") {
                            includeBuildTypeValues("debug")
                        }
                    }
                }
            """.trimIndent()
        )
        val failure = library.executor().expectFailure().run("clean", "publish")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "Using multipleVariants publishing DSL multiple times to publish variants " +
                        "to the same component \"custom\" is not allowed.")
        }
    }

    @Test
    fun testMixVariantPublishingWithSameComponentName() {
        addPublication(DEFAULT)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    buildTypes {
                        create("default") {
                            initWith debug
                        }
                    }
                    publishing {
                        multipleVariants {
                            allVariants()
                        }
                        singleVariant("default")
                    }
                }
            """.trimIndent()
        )
        val failure = library.executor().expectFailure().run("clean", "publish")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "Publishing variants to the \"default\" component using both singleVariant and " +
                        "multipleVariants publishing DSL is not allowed."
                )
        }
    }

    @Test
    fun testUsingNonExistingDimension() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants {
                            includeFlavorDimensionAndValues("randomDimension", "free", "paid")
                        }
                    }
                }
            """.trimIndent()
        )
        val failure = library.executor().expectFailure().run("help")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "Using non-existing dimension \"randomDimension\" when selecting variants to be " +
                        "published in multipleVariants publishing DSL."
            )
        }
    }

    @Test
    fun testUsingNonExistingFlavorValue() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    flavorDimensions 'version'
                    productFlavors {
                        free {}
                        paid {}
                    }

                    publishing {
                        multipleVariants {
                            includeFlavorDimensionAndValues("version", "free", "randomValue")
                        }
                    }
                }
            """.trimIndent()
        )
        val failure = library.executor().expectFailure().run("help")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "Using non-existing flavor value \"randomValue\" when selecting variants to be " +
                        "published in multipleVariants publishing DSL."
            )
        }
    }

    @Test
    fun testUsingNonExistingBuildType() {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        multipleVariants {
                            includeBuildTypeValues("debug", "random")
                        }
                    }
                }
            """.trimIndent()
        )
        val failure = library.executor().expectFailure().run("help")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains(
                "Using non-existing build type \"random\" when selecting variants to be " +
                        "published in multipleVariants publishing DSL."
            )
        }
    }

    @Test
    fun testDisableComponentCreationFlagWarningMessage() {
        var result = library
            .executor()
            .with(OptionalBooleanOption.DISABLE_AUTOMATIC_COMPONENT_CREATION, true)
            .run("help")
        result.stdout.use {
            ScannerSubject.assertThat(it).doesNotContain(
                "'android.disableAutomaticComponentCreation=true' is deprecated")
        }
        result = library
            .executor()
            .with(OptionalBooleanOption.DISABLE_AUTOMATIC_COMPONENT_CREATION, false)
            .run("help")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "'android.disableAutomaticComponentCreation=false' is deprecated")
        }
    }

    private fun addPublication(componentName: String) {
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                afterEvaluate {
                    publishing {

                        publications {
                            myPublication(MavenPublication) {
                                groupId = 'com.example.android'
                                artifactId = 'myLib'
                                version = '1.0'

                                from components.$componentName
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    companion object {
        private const val APP_MODULE = ":app"
        private const val LIBRARY_MODULE = ":library"
        private const val LIBRARY_PACKAGE = "com.example.lib"
        private const val RELEASE: String = "release"
        private const val CUSTOM: String = "custom"
        private const val DEFAULT: String = "default"
    }
}
