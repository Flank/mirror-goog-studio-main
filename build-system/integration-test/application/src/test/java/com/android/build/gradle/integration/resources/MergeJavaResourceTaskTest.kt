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
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests related to [MergeJavaResourceTask]
 */
class MergeJavaResourceTaskTest {

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestApp(
        MinimalSubProject.app("com.example.test")).create()

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
        assertThat(build.didWorkTasks).contains(":compileDebugJavaWithJavac")
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
        assertThat(build.didWorkTasks).contains(":compileDebugJavaWithJavac")
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
        val newSourceFile = File(project.mainJavaResDir, "com/android/tests/basic/library.so")
        assertThat(newSourceFile.exists()).isFalse()
        FileUtils.writeToFile(newSourceFile, "some_native_lib".trimIndent())
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