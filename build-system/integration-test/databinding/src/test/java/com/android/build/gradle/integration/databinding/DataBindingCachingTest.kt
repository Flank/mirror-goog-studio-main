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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.IOException

/**
 * Integration test to ensure cacheability when data binding is used
 * (https://issuetracker.google.com/69243050).
 */
@RunWith(FilterableParameterized::class)
class DataBindingCachingTest(private val withKotlin: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "withKotlin_{0}")
        @JvmStatic
        fun parameters() = listOf(
            arrayOf(true),
            arrayOf(false)
        )

        const val GRADLE_BUILD_CACHE = "gradle-build-cache"
        const val DATA_BINDING_GEN_BASE_CLASSES_TASK = ":dataBindingGenBaseClassesDebug"
        const val JAVA_COMPILE_TASK = ":compileDebugJavaWithJavac"
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("databinding")
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE"))
        .withKotlinGradlePlugin(true)
        .withName("project")
        .create()

    @get:Rule
    val projectCopy = GradleTestProject.builder()
        .fromTestProject("databinding")
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE"))
        .withKotlinGradlePlugin(true)
        .withName("projectCopy")
        .create()

    @Before
    @Throws(IOException::class)
    fun setUp() {
        if (withKotlin) {
            for (project in listOf(project, projectCopy)) {
                TestFileUtils.searchAndReplace(
                    project.buildFile,
                    "apply plugin: 'com.android.application'",
                    "apply plugin: 'com.android.application'\n" +
                            "apply plugin: 'kotlin-android'\n" +
                            "apply plugin: 'kotlin-kapt'"
                )
            }
        }
    }

    @Test
    fun `test main resources located within root project directory, expect cacheable tasks`() {
        // Build the first project
        val buildCacheDir = File(project.testDir.parent, GRADLE_BUILD_CACHE)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
        project.executor().withArgument("--build-cache").run("clean", JAVA_COMPILE_TASK)
        assertThat(buildCacheDir).exists()

        // Build the second project that is identical to the first project but has a different
        // location
        val result = projectCopy.executor().withArgument("--build-cache")
            .run("clean", JAVA_COMPILE_TASK)

        // Check that the relevant tasks are cacheable
        assertThat(result.getTask(DATA_BINDING_GEN_BASE_CLASSES_TASK)).wasFromCache()
        assertThat(result.getTask(JAVA_COMPILE_TASK)).wasFromCache()

        // Clean up
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }

    @Test
    fun `test main resources located outside root project directory, expect non-cacheable tasks`() {
        // Add some resources outside of the root project directory
        for (project in listOf(project, projectCopy)) {
            val resDirOutsideProject = "../resOutside${project.name}"
            FileUtils.mkdirs(project.file("$resDirOutsideProject/layout"))
            FileUtils.copyFile(
                project.file("src/main/res/layout/activity_main.xml"),
                project.file("$resDirOutsideProject/layout/activity_main_copy.xml")
            )
            project.buildFile.appendText(
                """
                android.sourceSets.main {
                    res.srcDirs = res.srcDirs + ['$resDirOutsideProject']
                }
                """
            )
        }

        // Build the first project
        val buildCacheDir = File(project.testDir.parent, GRADLE_BUILD_CACHE)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
        project.executor().withArgument("--build-cache").run("clean", JAVA_COMPILE_TASK)
        assertThat(buildCacheDir).exists()

        // Build the second project that is identical to the first project but has a different
        // location
        val result = projectCopy.executor().withArgument("--build-cache")
            .run("clean", JAVA_COMPILE_TASK)

        // Check that the relevant tasks are not cacheable
        assertThat(result.getTask(DATA_BINDING_GEN_BASE_CLASSES_TASK)).didWork()
        if (withKotlin) {
            // Data binding was processed by Kapt, so it doesn't make JavaCompile non-cacheable
            assertThat(result.getTask(JAVA_COMPILE_TASK)).wasFromCache()
        } else {
            assertThat(result.getTask(JAVA_COMPILE_TASK)).didWork()
        }

        // Clean up
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }
}