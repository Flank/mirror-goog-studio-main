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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

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
                    ":lib:generateReleaseAssets",
                    ":lib:generateReleaseResources",
                    ":lib:preBuild",
                    ":lib:preReleaseBuild"
                ),
                FROM_CACHE to setOf(
                    ":lib:compileReleaseJavaWithJavac",
                    ":lib:copyReleaseJniLibsProjectAndLocalJars",
                    ":lib:extractReleaseAnnotations",
                    ":lib:generateReleaseBuildConfig",
                    ":lib:generateReleaseResValues",
                    ":lib:generateReleaseRFile",
                    ":lib:javaPreCompileRelease",
                    ":lib:mergeReleaseConsumerProguardFiles",
                    ":lib:mergeReleaseGeneratedProguardFiles",
                    ":lib:mergeReleaseJavaResource",
                    ":lib:mergeReleaseJniLibFolders",
                    ":lib:mergeReleaseNativeLibs",
                    ":lib:mergeReleaseShaders",
                    ":lib:mergeReleaseResources",
                    ":lib:packageReleaseAssets",
                    ":lib:packageReleaseResources",
                    ":lib:parseReleaseLocalResources",
                    ":lib:processReleaseManifest",
                    ":lib:stripReleaseDebugSymbols",
                    ":lib:syncReleaseLibJars",
                    ":lib:verifyReleaseResources"
                ),
                /*
                 * Tasks that should be cacheable but are not yet cacheable.
                 *
                 * If you add a task to this list, remember to file a bug for it.
                 */
                DID_WORK to setOf(
                    ":lib:bundleReleaseAar" /*Bug 121275773 */,
                    ":lib:prepareLintJarForPublish" /* Bug 120413672 */
                ),
                SKIPPED to setOf(
                    ":lib:packageReleaseRenderscript",
                    ":lib:assembleRelease",
                    ":lib:compileReleaseAidl",
                    ":lib:compileReleaseRenderscript",
                    ":lib:compileReleaseShaders",
                    ":lib:processReleaseJavaRes"
                ),
                FAILED to setOf()
            )
    }

    @get:Rule
    val projectCopy1 = setUpTestProject("projectCopy1")

    @get:Rule
    val projectCopy2 = setUpTestProject("projectCopy2")

    @get:Rule
    val buildCacheDirRoot = TemporaryFolder()

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return GradleTestProject.builder()
            .fromTestApp(HelloWorldLibraryApp())
            .withName(projectName)
            .dontOutputLogOnFailure()
            .create()
    }

    @Test
    fun testRelocatability() {
        val buildCacheDir = buildCacheDirRoot.root.resolve(GRADLE_BUILD_CACHE_DIR)

        CacheabilityTestHelper
            .forProjects(
                projectCopy1,
                projectCopy2)
            .withBuildCacheDir(buildCacheDir)
            .withTasks("clean", ":lib:assembleRelease")
            .hasUpToDateTasks(EXPECTED_TASK_STATES.getValue(UP_TO_DATE))
            .hasFromCacheTasks(EXPECTED_TASK_STATES.getValue(FROM_CACHE))
            .hasDidWorkTasks(EXPECTED_TASK_STATES.getValue(DID_WORK))
            .hasSkippedTasks(EXPECTED_TASK_STATES.getValue(SKIPPED))
            .hasFailedTasks(EXPECTED_TASK_STATES.getValue(FAILED))
    }
}
