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

package com.android.build.gradle.integration.cacheability

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.truth.TaskStateList
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils

import com.google.common.collect.Sets
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Tests cacheability of tasks. Similar to [CacheabilityTest], but builds the release version of a
 * library module in order to verify the cacheability of a different set of tasks.
 *
 * See https://guides.gradle.org/using-build-cache/ for information on the Gradle build cache.
 */
@RunWith(JUnit4::class)
class LibraryCacheabilityTest {

    companion object {

        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"

        /**
         * The expected states of tasks when running a second build with the Gradle build cache
         * enabled from an identical project at a different location.
         */
        private val EXPECTED_TASK_STATES =
            mapOf(
                UP_TO_DATE to setOf(
                    ":app:clean",
                    ":lib:clean",
                    ":lib:compileReleaseSources",
                    ":lib:generateReleaseResources",
                    ":lib:preBuild",
                    ":lib:preReleaseBuild"
                ),
                FROM_CACHE to setOf(
                    ":lib:compileReleaseJavaWithJavac",
                    ":lib:extractReleaseAnnotations",
                    ":lib:generateReleaseBuildConfig",
                    ":lib:generateReleaseResValues",
                    ":lib:generateReleaseRFile",
                    ":lib:javaPreCompileRelease",
                    ":lib:mergeReleaseJniLibFolders",
                    ":lib:mergeReleaseShaders",
                    ":lib:mergeReleaseResources",
                    ":lib:packageReleaseAssets",
                    ":lib:packageReleaseResources",
                    ":lib:processReleaseManifest",
                    ":lib:verifyReleaseResources"
                ),
                DID_WORK to setOf(
                    ":lib:bundleReleaseAar",
                    ":lib:checkReleaseManifest",
                    ":lib:compileReleaseShaders",
                    ":lib:mergeReleaseConsumerProguardFiles",
                    ":lib:prepareLintJar",
                    ":lib:transformClassesAndResourcesWithSyncLibJarsForRelease",
                    ":lib:transformNativeLibsWithMergeJniLibsForRelease",
                    ":lib:transformNativeLibsWithStripDebugSymbolForRelease",
                    ":lib:transformNativeLibsWithSyncJniLibsForRelease",
                    ":lib:transformResourcesWithMergeJavaResForRelease"
                ),
                SKIPPED to setOf(
                    ":lib:packageReleaseRenderscript",
                    ":lib:assembleRelease",
                    ":lib:compileReleaseAidl",
                    ":lib:compileReleaseRenderscript",
                    ":lib:generateReleaseAssets",
                    ":lib:generateReleaseSources",
                    ":lib:processReleaseJavaRes"
                ),
                FAILED to setOf()
            )

        /**
         * Tasks that should be cacheable but are not yet cacheable.
         *
         * If you add a task to this list, remember to file a bug for it. The master bug for this
         * list is Bug 69668176.
         */
        private val NOT_YET_CACHEABLE = setOf(
            ":lib:bundleReleaseAar" /*Bug 121275773 */,
            ":lib:checkReleaseManifest" /* Bug 74595857 */,
            ":lib:compileReleaseShaders" /* Bug 120413401 */,
            ":lib:mergeReleaseConsumerProguardFiles" /* Bug 121276920 */,
            ":lib:prepareLintJar" /* Bug 120413672 */,
            ":lib:transformClassesAndResourcesWithSyncLibJarsForRelease" /* Bug 121275815 */,
            ":lib:transformNativeLibsWithMergeJniLibsForRelease" /* Bug 74595223 */,
            ":lib:transformNativeLibsWithStripDebugSymbolForRelease" /* Bug 120414535 */,
            ":lib:transformNativeLibsWithSyncJniLibsForRelease" /* Bug 121275531 */,
            ":lib:transformResourcesWithMergeJavaResForRelease"  /* Bug 74595224 */
        )

        /**
         * Tasks that are never cacheable.
         */
        private val NEVER_CACHEABLE = setOf<String>()
    }

    @get:Rule
    var projectCopy1 = GradleTestProject.builder()
        .fromTestApp(HelloWorldLibraryApp())
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE_DIR"))
        .withName("projectCopy1")
        .dontOutputLogOnFailure()
        .create()

    @get:Rule
    var projectCopy2 = GradleTestProject.builder()
        .fromTestApp(HelloWorldLibraryApp())
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE_DIR"))
        .withName("projectCopy2")
        .dontOutputLogOnFailure()
        .create()

    @Test
    fun testRelocatability() {
        // Build the first project
        val buildCacheDir = File(projectCopy1.testDir.parent, GRADLE_BUILD_CACHE_DIR)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
        // The task :lib:assembleRelease is run in order to run the VerifyLibraryResources task,
        // which is only created for release variants
        projectCopy1.executor().withArgument("--build-cache").run("clean", ":lib:assembleRelease")

        // Check that the build cache has been populated
        assertThat(buildCacheDir).exists()

        // Build the second project
        val result =
            projectCopy2.executor().withArgument("--build-cache")
                .run("clean", ":lib:assembleRelease")

        // When running this test with bazel, StripDebugSymbolTransform does not run as the NDK
        // directory is not available. We need to remove that task from the expected tasks' states.
        var expectedDidWorkTasks = EXPECTED_TASK_STATES[DID_WORK]!!
        if (result.findTask(":lib:transformNativeLibsWithStripDebugSymbolForRelease") == null) {
            expectedDidWorkTasks =
                    expectedDidWorkTasks.minus(":lib:transformNativeLibsWithStripDebugSymbolForRelease")
        }

        // Check that the tasks' states are as expected
        assertThat(result.upToDateTasks)
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[UP_TO_DATE]!!)
        assertThat(result.fromCacheTasks)
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[FROM_CACHE]!!)
        assertThat(result.didWorkTasks).containsExactlyElementsIn(expectedDidWorkTasks)
        assertThat(result.skippedTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[SKIPPED]!!)
        assertThat(result.failedTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[FAILED]!!)

        // Sanity-check that all the tasks that did work (were not cacheable) have been looked at
        // and categorized into either NOT_YET_CACHEABLE or NEVER_CACHEABLE.
        assertThat(EXPECTED_TASK_STATES[DID_WORK]).containsExactlyElementsIn(
            Sets.union(NOT_YET_CACHEABLE, NEVER_CACHEABLE)
        )

        // Clean up the cache
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }
}
