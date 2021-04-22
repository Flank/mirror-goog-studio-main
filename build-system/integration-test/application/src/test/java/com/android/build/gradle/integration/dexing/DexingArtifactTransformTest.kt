/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.internal.dependency.DexingNoClasspathTransform
import com.android.build.gradle.internal.dependency.DexingWithClasspathTransform
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.internal.tasks.DexingExternalLibArtifactTransform
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class DexingArtifactTransformTest {

    @Rule
    @JvmField
    val project =
        GradleTestProject.builder().fromTestApp(
            MinimalSubProject.app("com.example.test")
        ).create()

    @Test
    fun testMonoDex() {
        project.buildFile.appendText("\nandroid.defaultConfig.multiDexEnabled = false")
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).containsAllIn(listOf(":mergeExtDexDebug", ":mergeDexDebug"))
        assertThat(result.tasks).doesNotContain(":mergeLibDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeProjectDexDebug")
        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.getFolderName(),
                        "debug", "BuildConfig.jar").exists()) {
            assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                    .containsClass("Lcom/example/test/BuildConfig;")
        }
    }

    @Test
    fun testMultiDex() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).containsAllIn(
            listOf(
                ":mergeExtDexDebug",
                ":mergeProjectDexDebug",
                ":mergeLibDexDebug"
            )
        )
        assertThat(result.tasks).doesNotContain(":mergeDexDebug")
        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.getFolderName(),
                        "debug",
                        "BuildConfig.jar").exists()) {
            assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                    .containsClass("Lcom/example/test/BuildConfig;")
        }
    }

    @Test
    fun testLegacyMultiDex() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 19
        """.trimIndent()
        )
        val result = executor().run("assembleDebug")
        // Merge legacy multidex in a single task. This is so synthesized classes that originate
        // from the main dex classes are packaged in the primary dex.
        assertThat(result.tasks).contains(":mergeDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeExtDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeLibDexDebug")
        assertThat(result.tasks).doesNotContain(":mergeProjectDexDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG))
                .containsClass("Landroid/support/multidex/MultiDexApplication;")
    }

    @Test
    fun testAndroidTest() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.multiDexEnabled = true
            android.defaultConfig.minSdkVersion = 21
        """.trimIndent()
        )
        val result = executor().run("assembleAndroidTest")
        assertThat(result.tasks).containsAllIn(
            listOf(
                ":mergeExtDexDebugAndroidTest",
                ":mergeLibDexDebugAndroidTest",
                ":mergeProjectDexDebugAndroidTest"
            )
        )
    }

    @Test
    fun testExternalDeps() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(35)

        assertThat(
            File(
                InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE.getOutputDir(project.buildDir),
                "debug/out"
            ).listFiles()
        ).named("dexing task output for external libs").isEmpty()

        project.buildFile.appendText("\nandroid.defaultConfig.minSdkVersion = 26")
        executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(38)
    }

    @Test
    fun testAddingExternalDeps() {
        project.buildFile.appendText("\n" +
            """
            android.defaultConfig.minSdkVersion = 21
            android.defaultConfig.multiDexEnabled = true
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).hasDexVersion(35)

        project.buildFile.appendText(
            """

            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }
        """.trimIndent()
        )
        val run = executor().run("assembleDebug")
        assertThat(run.didWorkTasks).contains(":mergeExtDexDebug")
        assertThat(run.upToDateTasks).containsAllOf(":mergeLibDexDebug", ":mergeProjectDexDebug")
    }

    @Test
    fun testDesugaringDoesUseNewPipeline() {
        project.buildFile.appendText("\nandroid.compileOptions.targetCompatibility 1.8")
        val result = executor().run("assembleDebug")
        assertThat(result.tasks).containsAllIn(listOf(":mergeExtDexDebug", ":mergeDexDebug"))
    }

    /** Regression test for b/129910310. */
    @Test
    fun testGeneratedBytecodeIsProcessed() {
        TestInputsGenerator.jarWithEmptyClasses(
            project.projectDir.resolve("generated-classes.jar").toPath(), setOf("test/A")
        )

        project.buildFile.appendText("\n" +
            """
            android.applicationVariants.all { variant ->
                def generated = project.files("generated-classes.jar")
                variant.registerPostJavacGeneratedBytecode(generated)
            }
        """.trimIndent()
        )
        val result =
            project.executor().run("assembleDebug")
        assertThatApk(project.getApk(GradleTestProject.ApkType.DEBUG)).containsClass("Ltest/A;")
    }

    @Test
    fun testDesugaringWithMinSdk24() {
        project.buildFile.appendText("\n" + """
            android.defaultConfig.minSdkVersion 24
            android.compileOptions.targetCompatibility 1.8
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent())
        executor().run("assembleDebug")
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).doesNotContain(DexingWithClasspathTransform::class.java.simpleName)
        }
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).contains(DexingNoClasspathTransform::class.java.simpleName)
        }
    }

    @Test
    fun testExtLibsDexingWithTransforms() {
        project.buildFile.appendText("\n" +
                """
            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .run("assembleDebug")
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).contains(DexingExternalLibArtifactTransform::class.java.simpleName)
        }
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).doesNotContain(DexingNoClasspathTransform::class.java.simpleName)
        }
    }

    @Test
    fun testAllDexingTransformsDisabled() {
        project.buildFile.appendText(
            "\n" +
                    """
            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS, false)
            .run("assembleDebug")
        project.buildResult.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner)
                .doesNotContain(DexingExternalLibArtifactTransform::class.java.simpleName)
        }

        assertThat(
            InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS.getOutputDir(
                project.buildDir
            ).resolve("debug/out").listFiles()
        ).isEmpty()
        assertThat(
            InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE.getOutputDir(project.buildDir).resolve(
                "debug/out"
            ).listFiles()
        ).isNotEmpty()
    }

    /** Regression test for b/154712997. */
    @Test
    fun testIncrementalBuildsWithArtifactTransformsDisabledAndClasspathSensitivity() {
        // Generate 2 identical libraries.
        project.projectDir.resolve("mavenRepo").also {
            it.mkdirs()
            MavenRepoGenerator(
                listOf(
                    MavenRepoGenerator.Library(
                        "com.example:lib:1.0",
                        TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/MyClass"))
                    ),
                    MavenRepoGenerator.Library(
                        "com.example:lib:2.0",
                        TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/MyClass"))
                    )
                )
            ).generate(it.toPath())
        }
        project.buildFile.appendText(
                    """

repositories {
    maven { url 'mavenRepo' }
}
dependencies {
    implementation 'com.example:lib:1.0'
}
        """.trimIndent()
        )
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS, false)
            .run("assembleDebug")

        // Modify external dependencies so dexing task actually needs to run.
        project.buildFile.readText().let {
            val newBuildFile = it.replace("com.example:lib:1.0", "com.example:lib:2.0") + """

dependencies {
    implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
}
            """.trimIndent()
            project.buildFile.writeText(newBuildFile)
        }

        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS, false)
            .run("assembleDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThatApk(it).containsClass("Landroid/support/v4/app/NavUtils;")
        }
    }

    @Test
    fun testEnablingAndDisablingExtLibDexingTransform() {
        project.buildFile.appendText(
            "\n" +
                    """
            android.defaultConfig.minSdkVersion = 21
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .run("assembleDebug")
        assertThat(
            InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS.getOutputDir(
                project.buildDir
            ).resolve("debug/out").listFiles()
        ).isNotEmpty()
        assertThat(
            InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE.getOutputDir(project.buildDir).resolve(
                "debug/out"
            ).listFiles()
        ).isEmpty()

        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS, false)
            .run("assembleDebug")
        assertThat(
            InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS.getOutputDir(
                project.buildDir
            ).resolve("debug/out").listFiles()
        ).isEmpty()
        assertThat(
            InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE.getOutputDir(project.buildDir).resolve(
                "debug/out"
            ).listFiles()
        ).isNotEmpty()
    }

    @Test
    fun testAddingExternalDepsWithExtLibsDexingTransform() {
        project.buildFile.appendText(
            "\n" +
                    """
            android.defaultConfig.minSdkVersion = 21
            android.defaultConfig.multiDexEnabled = true
            dependencies {
                implementation 'com.android.support:support-core-utils:$SUPPORT_LIB_VERSION'
            }
        """.trimIndent()
        )
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .run("assembleDebug")
        val extLibsFile = InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS.getOutputDir(
            project.buildDir
        ).resolve("debug/out")
        val extLibsTimestamp = extLibsFile.listFiles()!!.first().lastModified()
        val projectFile = InternalArtifactType.PROJECT_DEX_ARCHIVE.getOutputDir(project.buildDir).resolve("debug/out")
        val projectTimestamp = projectFile.listFiles()!!.first().lastModified()

        project.buildFile.appendText(
            """

            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }
        """.trimIndent()
        )
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .run("assembleDebug")

        assertThat(extLibsFile.listFiles()!!.first().lastModified()).named("external lib dex timestamp in incremental build that added an external dependency")
            .isGreaterThan(extLibsTimestamp)
        assertThat(projectFile.listFiles()!!.first().lastModified()).named("project dex timestamp in incremental build that added an external dependency")
            .isEqualTo(projectTimestamp)

        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG)).containsClass("Lcom/google/common/collect/ImmutableList;")
    }

    private fun executor() =
        project.executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, true)
            .with(BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM, true)
}
