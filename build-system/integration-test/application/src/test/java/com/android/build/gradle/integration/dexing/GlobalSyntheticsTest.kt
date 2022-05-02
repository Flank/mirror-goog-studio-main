/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.apk.AndroidArchive
import com.google.common.io.Resources
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files

@RunWith(Parameterized::class)
class GlobalSyntheticsTest(private val dexType: DexType) {

    enum class DexType {
        MONO, LEGACY, NATIVE
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dexType_{0}")
        fun parameters() = listOf(DexType.MONO, DexType.LEGACY, DexType.NATIVE)

        const val exceptionGlobalTriggerClass = "IllformedLocaleExceptionUsage"
        const val exceptionGlobalDex = "Landroid/icu/util/IllformedLocaleException;"
        const val recordGlobalDex = "Lcom/android/tools/r8/RecordTag;"
    }

    private val recordJarUrl = Resources.getResource(
        GlobalSyntheticsTest::class.java,
        "GlobalSyntheticsTest/record.jar"
    )

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library(
                "com.example:myjar:1", Resources.toByteArray(recordJarUrl))
        )
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .withAdditionalMavenRepo(mavenRepo)
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject("app", MinimalSubProject.app("com.example.app"))
                .subproject("lib", MinimalSubProject.lib("com.example.lib"))
                .build()
        ).create()

    private lateinit var app: GradleTestProject
    private lateinit var lib: GradleTestProject

    @Before
    fun setUp() {
        app = project.getSubproject("app")
        lib = project.getSubproject("lib")

        TestFileUtils.appendToFile(
            app.buildFile,
            """

                dependencies {
                    implementation project(":lib")
                }
            """.trimIndent()
        )

        val minSdkVersion = if (dexType == DexType.NATIVE) 21 else 20

        TestFileUtils.appendToFile(
            app.buildFile,
            "\nandroid.defaultConfig.minSdkVersion = $minSdkVersion\n"
        )
        if (dexType == DexType.LEGACY) {
            TestFileUtils.appendToFile(
                app.buildFile,
                "android.defaultConfig.multiDexEnabled = true"
            )
        }
    }

    @Test
    fun testGlobalFromAppAndFileDep() {
        createExceptionGlobalSourceFile(
            app.mainSrcDir.resolve("com/example/app/$exceptionGlobalTriggerClass.java"),
            "com.example.app",
            exceptionGlobalTriggerClass
        )
        addFileDependencies(app)

        executor().run("assembleDebug")

        val localeGlobalFromApp = InternalArtifactType.GLOBAL_SYNTHETICS_PROJECT
            .getOutputDir(app.buildDir).resolve("debug/out/com/example/app/$exceptionGlobalTriggerClass.globals")
        Truth.assertThat(localeGlobalFromApp.exists()).isTrue()
        val recordGlobalFromFileDep = InternalArtifactType.GLOBAL_SYNTHETICS_FILE_LIB
            .getOutputDir(app.buildDir).resolve("debug/0_record.jar")
        Truth.assertThat(recordGlobalFromFileDep.exists()).isTrue()

        if (dexType == DexType.NATIVE) {
            val globalDex = InternalArtifactType.GLOBAL_SYNTHETICS_DEX.getOutputDir(app.buildDir)
                .resolve("debug/classes.dex")
            Truth.assertThat(globalDex.exists()).isTrue()
        }

        checkPackagedGlobal(exceptionGlobalDex)
        checkPackagedGlobal(recordGlobalDex)
    }

    @Test
    fun testGlobalFromLibAndExternalDep() {
        createExceptionGlobalSourceFile(
            lib.mainSrcDir.resolve("com/example/lib/$exceptionGlobalTriggerClass.java"),
            "com.example.lib",
            exceptionGlobalTriggerClass
        )

        app.buildFile.appendText(
            """

                dependencies {
                    implementation 'com.example:myjar:1'
                }
            """.trimIndent()
        )

        executor().run("assembleDebug")

        if (dexType == DexType.NATIVE) {
            val globalDex = InternalArtifactType.GLOBAL_SYNTHETICS_DEX.getOutputDir(app.buildDir)
                .resolve("debug/classes.dex")
            Truth.assertThat(globalDex.exists()).isTrue()
        }

        checkPackagedGlobal(exceptionGlobalDex)
        checkPackagedGlobal(recordGlobalDex)
    }

    @Test
    fun testDeDupGlobal() {
        createExceptionGlobalSourceFile(
            app.mainSrcDir.resolve("com/example/app/$exceptionGlobalTriggerClass.java"),
            "com.example.app",
            exceptionGlobalTriggerClass
        )

        createExceptionGlobalSourceFile(
            lib.mainSrcDir.resolve("com/example/lib/$exceptionGlobalTriggerClass.java"),
            "com.example.lib",
            exceptionGlobalTriggerClass
        )

        // test the pipeline when artifact transform is disabled
        executor()
            .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
            .run("assembleDebug")

        val localeGlobalFromApp = InternalArtifactType.GLOBAL_SYNTHETICS_PROJECT
            .getOutputDir(app.buildDir).resolve("debug/out/com/example/app/$exceptionGlobalTriggerClass.globals")
        Truth.assertThat(localeGlobalFromApp.exists()).isTrue()

        val localeGlobalFromLib = InternalArtifactType.GLOBAL_SYNTHETICS_SUBPROJECT
            .getOutputDir(app.buildDir).resolve("debug/out")
        Truth.assertThat(localeGlobalFromLib.listFiles()).hasLength(1)

        checkPackagedGlobal(exceptionGlobalDex)
    }

    private fun createExceptionGlobalSourceFile(source: File, pkg: String, name: String) {
        source.let {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.appendText("package $pkg;")
            it.appendText("public class $name {")
            it.appendText(
                """
                        public void function() {
                            try {
                                throw new android.icu.util.IllformedLocaleException();
                            } catch (android.icu.util.IllformedLocaleException e) {}
                        }
                    }
                """.trimIndent()
            )
        }
    }

    private fun addFileDependencies(app: GradleTestProject) {
        val recordJar = "record.jar"

        Files.write(
            app.projectDir.resolve(recordJar).toPath(),
            Resources.toByteArray(recordJarUrl)
        )
        app.buildFile.appendText(
            """

                dependencies {
                    implementation files('$recordJar')
                }
            """.trimIndent()
        )
    }

    private fun checkPackagedGlobal(global: String) {
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)

        // there should only be a single global synthetics of specific type in the apk
        val dexes = apk.allDexes.filter {
            AndroidArchive.checkValidClassName(global)
            it.classes.keys.contains(global)
        }
        Truth.assertThat(dexes.size).isEqualTo(1)
    }

    private fun executor() = project.executor().with(BooleanOption.ENABLE_GLOBAL_SYNTHETICS, true)
}
