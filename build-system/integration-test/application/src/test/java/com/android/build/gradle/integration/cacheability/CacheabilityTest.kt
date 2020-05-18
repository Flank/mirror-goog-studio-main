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
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests cacheability of tasks.
 *
 * See https://guides.gradle.org/using-build-cache/ for information on the Gradle build cache.
 */
@RunWith(JUnit4::class)
class CacheabilityTest {

    companion object {

        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"

        /**
         * The expected states of tasks when running a second build with the Gradle build cache
         * enabled from an identical project at a different location.
         */
        private val EXPECTED_TASK_STATES =
            mapOf(
                // Sort by alphabetical order for easier searching
                UP_TO_DATE to setOf(
                    ":app:clean",
                    ":app:generateDebugAssets",
                    ":app:generateDebugResources",
                    ":app:preBuild",
                    ":app:preDebugBuild",
                    ":app:preDebugUnitTestBuild"
                ),
                FROM_CACHE to setOf(
                    ":app:bundleDebugClasses",
                    ":app:checkDebugDuplicateClasses",
                    ":app:compileDebugJavaWithJavac",
                    ":app:compileDebugUnitTestJavaWithJavac",
                    ":app:compressDebugAssets",
                    ":app:createDebugCompatibleScreenManifests",
                    ":app:dexBuilderDebug",
                    ":app:extractDeepLinksDebug",
                    ":app:generateDebugBuildConfig",
                    ":app:generateDebugResValues",
                    ":app:generateDebugUnitTestConfig",
                    ":app:javaPreCompileDebug",
                    ":app:javaPreCompileDebugUnitTest",
                    ":app:mergeDebugAssets",
                    ":app:mergeDebugJavaResource",
                    ":app:mergeDebugJniLibFolders",
                    ":app:mergeDebugNativeLibs",
                    ":app:mergeDebugShaders",
                    ":app:mergeDexDebug",
                    ":app:mergeExtDexDebug",
                    ":app:packageDebugUnitTestForUnitTest",
                    ":app:parseDebugIntegrityConfig",
                    ":app:processDebugMainManifest",
                    ":app:processDebugManifest",
                    ":app:processDebugManifestForPackage",
                    ":app:testDebugUnitTest",
                    ":app:validateSigningDebug"
            ),
                /*
                 * Tasks that should be cacheable but are not yet cacheable.
                 *
                 * If you add a task to this list, remember to file a bug for it.
                 */
                DID_WORK to setOf(
                    ":app:mergeDebugResources", /* Bug 141301405 */
                    ":app:packageDebug", /* Bug 74595859 */
                    ":app:processDebugResources" /* Bug 141301405 */
                ),
                SKIPPED to setOf(
                    ":app:assembleDebug",
                    ":app:compileDebugAidl",
                    ":app:compileDebugRenderscript",
                    ":app:compileDebugShaders",
                    ":app:compileDebugSources",
                    ":app:mergeDebugNativeDebugMetadata",
                    ":app:processDebugJavaRes",
                    ":app:processDebugUnitTestJavaRes",
                    ":app:stripDebugDebugSymbols"
                ),
                FAILED to setOf()
            )
    }

    @get:Rule
    val buildCacheDirRoot = TemporaryFolder()

    @get:Rule
    val projectCopy1 = setUpTestProject("projectCopy1")

    @get:Rule
    val projectCopy2 = setUpTestProject("projectCopy2")

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return with(EmptyActivityProjectBuilder()) {
            this.projectName = projectName
            this.withUnitTest = true
            build()
        }
    }

    @Before
    fun setUp() {
        for (project in listOf(projectCopy1, projectCopy2)) {
            // Set up the project such that we can check the cacheability of AndroidUnitTest task
            TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                "android { testOptions { unitTests { includeAndroidResources = true } } }"
            )
        }
    }

    @Test
    fun testRelocatability() {
        val buildCacheDir = buildCacheDirRoot.root.resolve(GRADLE_BUILD_CACHE_DIR)

        CacheabilityTestHelper(projectCopy1, projectCopy2, buildCacheDir)
            .runTasks(
                "clean",
                "assembleDebug",
                "testDebugUnitTest",
                ":app:parseDebugIntegrityConfig"
            )
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
