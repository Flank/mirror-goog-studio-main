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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithTextEntries
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests related to [MergeJavaResourceTask]
 */
class MergeJavaResourceTaskTest {

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library(
                "com.example:lib1:0.1",
                jarWithTextEntries("conflict_res" to "a")
            ),
            MavenRepoGenerator.Library(
                "com.example:lib2:0.1",
                jarWithTextEntries("conflict_res" to "b")
            )
        )
    )

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.app("com.example.test")
    )
        .withAdditionalMavenRepo(mavenRepo)
        .create()

    @Test
    fun ensureHelpfulErrorMessageOnConflict() {
        TestFileUtils.appendToFile(
            project.buildFile, """
            dependencies {
                implementation 'com.example:lib1:0.1'
                implementation 'com.example:lib2:0.1'
            }
        """
        )
        val failure = project.executor().expectFailure().run("assembleDebug")
        // Ensure that the inputs are included in the stderr output.
        assertThat(failure.stderr).contains("2 files found with path 'conflict_res' from inputs:\n - ")
    }

    @Test
    fun ensureNoJavacDependencyIfNoAnnotationProcessor() {
        val build = project.executor().run("clean", ":mergeDebugJavaResource")
        assertThat(build.didWorkTasks).doesNotContain(":compileDebugJavaWithJavac")
    }

    @Test
    fun ensureJavacDependencyIfAnnotationProcessor() {
        val emptyJar = project.file("empty.jar")
        assertThat(emptyJar.createNewFile()).isTrue()
        TestFileUtils.appendToFile(
            project.buildFile,
            "dependencies { annotationProcessor files('empty.jar') }"
        )
        val build = project.executor().run("clean", ":mergeDebugJavaResource")
        if (project.getIntermediateFile(
                InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.getFolderName()).exists()) {
            assertThat(build.didWorkTasks).doesNotContain(":compileDebugJavaWithJavac")
        } else {
            assertThat(build.didWorkTasks).contains(":compileDebugJavaWithJavac")
        }
    }

    @Test
    fun ensureJavacDependencyIfAnnotationProcessorAddedViaDefaultDependencies() {
        val emptyJar = project.file("empty.jar")
        assertThat(emptyJar.createNewFile()).isTrue()
        TestFileUtils.appendToFile(
            project.buildFile,
            """configurations['annotationProcessor'].defaultDependencies { dependencies ->
                |    dependencies.add(owner.project.dependencies.create(files('empty.jar')))
                |}""".trimMargin()
        )
        val build = project.executor().run("clean", ":mergeDebugJavaResource")
        if (project.getIntermediateFile(
                InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.getFolderName()).exists()) {
            assertThat(build.didWorkTasks).doesNotContain(":compileDebugJavaWithJavac")
        } else {
            assertThat(build.didWorkTasks).contains(":compileDebugJavaWithJavac")
        }
    }

    @Test
    fun ensureJavaResIsNotRunningWhenOnlyClassesChange() {
        project.execute("assembleDebug")
        val newSourceFile = File(project.mainSrcDir, "com/android/tests/basic/NewSourceFile.java")
        assertThat(newSourceFile.exists()).isFalse()
        FileUtils.writeToFile(newSourceFile, """
            package com.android.tests.basic;

            class NewSourceFile {
                public static int foo() {
                    return 154;
                }
            }
        """.trimIndent())
        val gradleBuildResult = project.executor().run("assembleDebug")
        val javaResTask = gradleBuildResult.findTask(":mergeDebugJavaResource")
        assertThat(javaResTask?.wasUpToDate()).isTrue()
    }

    @Test
    fun ensureJavaResIsNotRunningWhenOnlyNativeLibsChange() {
        project.execute("assembleDebug")
        val newNativeLib = File(project.mainJniLibsDir, "x86/library.so")
        assertThat(newNativeLib).doesNotExist()
        FileUtils.writeToFile(newNativeLib, "some_native_lib")
        assertThat(newNativeLib).exists()
        val gradleBuildResult = project.executor().run("assembleDebug")
        val javaResTask = gradleBuildResult.findTask(":mergeDebugJavaResource")
        assertThat(javaResTask?.wasUpToDate()).isTrue()
    }

    @Test
    fun ensureJavaResIsRunningWhenResourcesIsAdded() {
        project.execute("assembleDebug")
        val newSourceFile = File(project.mainJavaResDir, "com/android/tests/app.txt")
        assertThat(newSourceFile.exists()).isFalse()
        FileUtils.writeToFile(newSourceFile, """does_not_matter""".trimIndent())
        val gradleBuildResult = project.executor().run("assembleDebug")
        val javaResTask = gradleBuildResult.findTask(":mergeDebugJavaResource")
        assertThat(javaResTask?.wasUpToDate()).isFalse()
    }

    @Test
    fun ensureJavaResIsRunningWhenResourcesIsChanged() {
        project.execute("assembleDebug")
        val newSourceFile = File(project.mainJavaResDir, "com/android/tests/app.txt")
        assertThat(newSourceFile.exists()).isFalse()
        FileUtils.writeToFile(newSourceFile, """does_not_matter""".trimIndent())
        project.executor().run("assembleDebug")
        FileUtils.writeToFile(newSourceFile, """does_not_matter_version_2""".trimIndent())
        val gradleBuildResult = project.executor().run("assembleDebug")
        val javaResTask = gradleBuildResult.findTask(":mergeDebugJavaResource")
        assertThat(javaResTask?.wasUpToDate()).isFalse()
    }

    @Test
    fun ensureJavaResIsRunningWhenResourcesIsRemoved() {
        project.execute("assembleDebug")
        val newSourceFile = File(project.mainJavaResDir, "com/android/tests/app.txt")
        assertThat(newSourceFile.exists()).isFalse()
        FileUtils.writeToFile(newSourceFile, """does_not_matter""".trimIndent())
        project.executor().run("assembleDebug")
        assertThat(newSourceFile.delete()).isTrue()
        val gradleBuildResult = project.executor().run("assembleDebug")
        val javaResTask = gradleBuildResult.findTask(":mergeDebugJavaResource")
        assertThat(javaResTask?.wasUpToDate()).isFalse()
    }
}
