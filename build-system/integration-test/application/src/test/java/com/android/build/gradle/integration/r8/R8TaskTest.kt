/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.IntegerOption
import com.android.testutils.TestClassesGenerator
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class R8TaskTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(project.buildFile,
                """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testCheckDuplicateClassesTaskDidWork() {
        val buildResult =
                project.executor().run(":minifyDebugWithR8")
        assertThat(buildResult.didWorkTasks).contains(":checkDebugDuplicateClasses")
    }

    @Test
    fun testTestedClassesPassedAsClasspathToR8() {
        val buildResult =
                project.executor()
                        .withLoggingLevel(LoggingLevel.DEBUG)
                        .run(":assembleDebugAndroidTest")
        val appClasses = project.getIntermediateFile(
                InternalArtifactType.COMPILE_APP_CLASSES_JAR.getFolderName() + "/debug/classes.jar"
        );
        buildResult.stdout.use {
            ScannerSubject.assertThat(it)
                    .contains("[R8] Classpath classes: [$appClasses]")
        }
    }

    @Test
    fun testMissingKeepRules() {
        project.projectDir.resolve("lib.jar").also {
            val classToWrite = TestClassesGenerator.classWithEmptyMethods(
                    "A", "foo:()Ltest/B;", "bar:()Ltest/C;")
            ZipOutputStream(it.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("test/A.class"))
                zip.write(classToWrite)
                zip.closeEntry()
            }
        }
        project.buildFile.appendText("""

            dependencies {
                implementation files("lib.jar")
            }
        """.trimIndent())
        project.file("proguard-rules.pro").appendText("-keep class test.A { *; }")

        project.executor().expectFailure().run(":assembleDebug")
        val missingRules = project.buildDir.resolve("outputs/mapping/debug/missing_rules.txt")
        assertThat(missingRules).contentWithUnixLineSeparatorsIsExactly(
                """
                    # Please add these rules to your existing keep rules in order to suppress warnings.
                    # This is generated automatically by the Android Gradle plugin.
                    -dontwarn test.B
                    -dontwarn test.C
                """.trimIndent()
        )

        val result =
                project.executor()
                        .expectFailure()
                        .run(":assembleDebug")
        result.stderr.use {
            ScannerSubject.assertThat(it)
                    .contains("Missing classes detected while running R8.")
        }
    }

    @Test
    fun testOutputMainDexList() {
        enableMultiDex()
        project.executor().run(":assembleDebug")
        val mainDexListFile = InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST
            .getOutputDir(project.buildDir)
            .resolve("debug/mainDexList.txt")
        assertThat(mainDexListFile).exists()
    }

    @Test
    fun testMultiDexKeepFileDeprecation() {
        enableMultiDex()

        project.buildFile.resolveSibling("multidex-keep-file.txt").createNewFile()
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android.buildTypes.debug.multiDexKeepFile file('multidex-keep-file.txt')
            """.trimIndent()
        )

        val result = project.executor().run(":assembleDebug")
        result.stdout.use { scanner ->
            ScannerSubject.assertThat(scanner).contains(
                "WARNING:Using multiDexKeepFile property with R8 is deprecated and will be fully " +
                        "removed in AGP 8.0. Please migrate to use multiDexKeepProguard instead.")
        }
    }

    @Test
    fun testInjectedDeviceApi() {
        project.buildFile.appendText("""

            android.defaultConfig.minSdkVersion 21
        """.trimIndent())
        project.mainSrcDir.resolve("example/MyInterface.java").also {
            it.parentFile.mkdirs()
            it.resolveSibling("MyInterface.java").writeText("""
                package example;

                interface MyInterface {
                    static void printContent() { System.out.println("hello"); }
                }
            """.trimIndent())
        }
        project.file("proguard-rules.pro").appendText("""
            -keep class example.MyInterface* { *; }
            -dontobfuscate
        """.trimIndent())

        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
                .run("assembleDebug")
        val apkApi24 = project.getApk(
                GradleTestProject.ApkType.DEBUG,
                GradleTestProject.ApkLocation.Intermediates)
        assertThatApk(apkApi24).doesNotContainClass("Lexample/MyInterface$-CC;")

        project.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 23)
                .run("assembleDebug")
        val apkApi23 = project.getApk(
                GradleTestProject.ApkType.DEBUG,
                GradleTestProject.ApkLocation.Intermediates)
        assertThatApk(apkApi23).hasClass("Lexample/MyInterface$-CC;")
    }

    // regression test for b/210573363
    @Test
    fun testDefaultProguardRules() {
        project.executor().run("assembleDebug")
        TestFileUtils.searchAndReplace(
                project.buildFile,
                "getDefaultProguardFile('proguard-android-optimize.txt'),",
                ""
        )
        val result = project.executor().run("assembleDebug")
        assertTrue(result.didWorkTasks.contains(":minifyDebugWithR8"))
    }

    private fun enableMultiDex() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android {
                    defaultConfig {
                       multiDexEnabled true
                    }
                }
            """.trimIndent()
        )
    }
}
