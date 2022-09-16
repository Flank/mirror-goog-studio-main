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

import com.android.Version
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject
import com.android.testutils.truth.PathSubject.assertThat
import org.gradle.api.attributes.Usage
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
    fun testSingleVariantPublishing() {
        addPublication(RELEASE)
        TestFileUtils.appendToFile(
            library.buildFile,
            """

                android {
                    publishing {
                        singleVariant("release") {
                            // make sure app can consume library published with source and javadoc
                            withSourcesJar()
                            withJavadocJar()
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        app.execute("clean", "assembleDebug")
    }

    @Test
    fun testErrorMessageForNotUsingPublishingDsl() {
        addPublication(RELEASE)
        val result = library.executor().expectFailure().run("publish")
        result.stderr.use {
            ScannerSubject.assertThat(it).contains("""
                Could not get unknown property 'release' for SoftwareComponentInternal
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
        val module = project.projectDir
            .resolve("testrepo/com/example/android/myLib/1.0/myLib-1.0.module")
        assertThat(module).exists()
        assertThat(module).contains("""
            |    {
            |      "name": "freeDebugVariantCustomApiPublication",
            |      "attributes": {
            |        "com.android.build.api.attributes.BuildTypeAttr": "debug",
            |        "com.android.build.api.attributes.ProductFlavor:version": "free",
            |        "org.gradle.category": "library",
            |        "org.gradle.dependency.bundling": "external",
            |        "org.gradle.libraryelements": "aar",
            |        "org.gradle.usage": "java-api"
            |      },
            """.trimMargin())
        // todo re-enable config caching b/247126887
        val configCacheOption =
            if (Runtime.version().feature() == 17)
                BaseGradleExecutor.ConfigurationCaching.OFF
            else
                BaseGradleExecutor.ConfigurationCaching.ON
        val failure = app.executor().withConfigurationCaching(configCacheOption)
            .expectFailure().run("clean", "assembleInternalDebug")
        failure.stderr.use {
            ScannerSubject.assertThat(it).contains("Could not resolve com.example.android:myLib:1.0")
        }
    }

    // Regression test for b/241076233
    @Test
    fun testMultipleVariantPublishingWithNoBuildTypeAttribute() {
        addPublication(CUSTOM)
        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android {
                    flavorDimensions 'version'
                    productFlavors {
                        free {}
                        paid {}
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
                    }
                    publishing {
                        multipleVariants("$CUSTOM") {
                            includeBuildTypeValues("debug")
                            includeFlavorDimensionAndValues("version", "free", "paid")
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
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
    fun testConfigurationsNotHavingAgpVersionAttribute() {
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

        val module = project.projectDir
            .resolve("testrepo/com/example/android/myLib/1.0/myLib-1.0.module")
        PathSubject.assertThat(module).exists()
        PathSubject.assertThat(module).contains(Usage.USAGE_ATTRIBUTE.name)
        PathSubject.assertThat(module).doesNotContain(AgpVersionAttr.ATTRIBUTE.name)
        PathSubject.assertThat(module).doesNotContain(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    }

    // regression test for b/211725182
    @Test
    fun testAllVariantsWithProductFlavors() {
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
                    flavorDimensions 'price'
                    productFlavors {
                        free {}
                        paid {}
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
    }

    // regression for b/233511980
    // Test checks whether published library artifact has proper naming
    // in case there is a transformation in build.
    @Test
    fun testPublishingWithTransformation() {
        addPublication(RELEASE)
        TestFileUtils.appendToFile(
            library.buildFile, """
                    android.publishing.singleVariant('release')
            """.trimIndent())

        TestFileUtils.appendToFile(
            library.buildFile, """
    import org.gradle.api.DefaultTask
    import org.gradle.api.file.RegularFileProperty
    import org.gradle.api.tasks.InputFiles
    import org.gradle.api.tasks.TaskAction
    import org.gradle.api.provider.Property
    import org.gradle.api.tasks.Internal
    import com.android.build.api.artifact.SingleArtifact
    import org.gradle.api.tasks.OutputFile
    import java.nio.file.Files

    abstract class UpdateArtifactTask extends DefaultTask {
        @InputFiles
        abstract RegularFileProperty  getInitialArtifact()

        @OutputFile
        abstract RegularFileProperty getUpdatedArtifact()

        @TaskAction
        def taskAction() {
            // just make a copy to new location
            Files.copy(initialArtifact.get().asFile.toPath(), updatedArtifact.get().asFile.toPath())
        }
    }

     androidComponents {
            onVariants(selector().all(), { variant ->
                TaskProvider taskProvider = project.tasks.register(variant.getName() + 'UpdateArtifact', UpdateArtifactTask.class)
                    variant.artifacts.use(taskProvider)
                        .wiredWithFiles(
                            { it.getInitialArtifact() },
                            { it.getUpdatedArtifact() })
                        .toTransform(SingleArtifact.AAR.INSTANCE)
        })
    }
            """.trimIndent())

        library.execute("clean", "publish")
        PathSubject.assertThat(project.projectDir.resolve("testrepo/com/example/android/myLib/1.0/myLib-1.0.aar")).exists()
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
