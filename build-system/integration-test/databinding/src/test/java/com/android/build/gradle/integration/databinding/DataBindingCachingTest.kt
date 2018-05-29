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
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Integration test for the interaction between data binding and Gradle task output caching
 * (https://issuetracker.google.com/69243050).
 */
@RunWith(FilterableParameterized::class)
class DataBindingCachingTest(withKotlin: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "withKotlin_{0}")
        @JvmStatic
        fun parameters() = listOf(
            // arrayOf(true), /* TODO Test with Kotlin once issues with Kapt are fixed. */
            arrayOf(false)
        )

        const val GRADLE_BUILD_CACHE = "gradle-build-cache"
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject(if (withKotlin) "databindingAndKotlin" else "databinding")
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE"))
        .withName("project")
        .create()

    @Suppress("MemberVisibilityCanBePrivate")
    @get:Rule
    val projectCopy = GradleTestProject.builder()
        .fromTestProject(if (withKotlin) "databindingAndKotlin" else "databinding")
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE"))
        .withName("projectCopy")
        .create()

    @Test
    fun testSameProjectLocation() {
        // Run the first build to populate the Gradle build cache
        val buildCacheDir = File(project.testDir.parent, GRADLE_BUILD_CACHE)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)

        project.executor().withArgument("--build-cache").run("clean", "compileDebugJavaWithJavac")
        assertThat(buildCacheDir).exists()

        // In the second build, the Java compile task should get their outputs from the build cache
        val result = project.executor().withArgument("--build-cache")
            .run("clean", "compileDebugJavaWithJavac")
        assertThat(result.getTask(":compileDebugJavaWithJavac")).wasFromCache()

        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }

    @Test
    fun testDifferentProjectLocations() {
        // Build the first project to populate the Gradle build cache
        val buildCacheDir = File(project.testDir.parent, GRADLE_BUILD_CACHE)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)

        project.executor().withArgument("--build-cache").run("clean", "compileDebugJavaWithJavac")
        assertThat(buildCacheDir).exists()

        // Build the second project that is identical to the first project, uses the same build
        // cache, but has a different location. The Java compile task should still get their outputs
        // from the build cache.
        val result = projectCopy.executor().withArgument("--build-cache")
            .run("clean", "compileDebugJavaWithJavac")
        assertThat(result.getTask(":compileDebugJavaWithJavac")).wasFromCache()

        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }
}