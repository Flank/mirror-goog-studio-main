/*
 * Copyright (C) 2020 The Android Open Source Project
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
class DynamicFeaturesCacheabilityTest {

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

                    ":feature1:clean",
                    ":feature1:compileDebugSources",
                    ":feature1:generateDebugAssets",
                    ":feature1:generateDebugResources",
                    ":feature1:preBuild",
                    ":feature1:preDebugBuild",

                    ":feature2:clean",
                    ":feature2:compileDebugSources",
                    ":feature2:generateDebugAssets",
                    ":feature2:generateDebugResources",
                    ":feature2:preBuild",
                    ":feature2:preDebugBuild"
                ),
                FROM_CACHE to setOf(
                    ":app:bundleDebugClasses",
                    ":app:checkDebugDuplicateClasses",
                    ":app:checkDebugLibraries",
                    ":app:compileDebugJavaWithJavac",
                    ":app:createDebugCompatibleScreenManifests",
                    ":app:dexBuilderDebug",
                    ":app:extractDeepLinksDebug",
                    ":app:generateDebugBuildConfig",
                    ":app:generateDebugFeatureMetadata",
                    ":app:generateDebugFeatureTransitiveDeps",
                    ":app:generateDebugResValues",
                    ":app:javaPreCompileDebug",
                    ":app:mergeDebugAssets",
                    ":app:mergeDebugJavaResource",
                    ":app:mergeDebugJniLibFolders",
                    ":app:mergeDebugShaders",
                    ":app:mergeDexDebug",
                    ":app:multiDexListDebug",
                    ":app:preDebugBuild",
                    ":app:processDebugMainManifest",
                    ":app:processDebugManifest",
                    ":app:processDebugManifestForPackage",
                    ":app:signingConfigWriterDebug",
                    ":app:validateSigningDebug",

                    ":feature1:checkDebugDuplicateClasses",
                    ":feature1:compileDebugJavaWithJavac",
                    ":feature1:createDebugCompatibleScreenManifests",
                    ":feature1:dexBuilderDebug",
                    ":feature1:extractDeepLinksDebug",
                    ":feature1:generateDebugBuildConfig",
                    ":feature1:generateDebugFeatureTransitiveDeps",
                    ":feature1:generateDebugResValues",
                    ":feature1:javaPreCompileDebug",
                    ":feature1:mergeDebugAssets",
                    ":feature1:mergeDebugJavaResource",
                    ":feature1:mergeDebugJniLibFolders",
                    ":feature1:mergeDebugShaders",
                    ":feature1:mergeExtDexDebug",
                    ":feature1:mergeLibDexDebug",
                    ":feature1:mergeProjectDexDebug",
                    ":feature1:processApplicationManifestDebugForBundle",
                    ":feature1:processDebugMainManifest",
                    ":feature1:processDebugManifest",
                    ":feature1:processDebugManifestForPackage",
                    ":feature1:processManifestDebugForFeature",

                    ":feature2:checkDebugDuplicateClasses",
                    ":feature2:compileDebugJavaWithJavac",
                    ":feature2:createDebugCompatibleScreenManifests",
                    ":feature2:dexBuilderDebug",
                    ":feature2:extractDeepLinksDebug",
                    ":feature2:generateDebugBuildConfig",
                    ":feature2:generateDebugFeatureTransitiveDeps",
                    ":feature2:generateDebugResValues",
                    ":feature2:javaPreCompileDebug",
                    ":feature2:mergeDebugAssets",
                    ":feature2:mergeDebugJavaResource",
                    ":feature2:mergeDebugJniLibFolders",
                    ":feature2:mergeDebugShaders",
                    ":feature2:mergeExtDexDebug",
                    ":feature2:mergeLibDexDebug",
                    ":feature2:mergeProjectDexDebug",
                    ":feature2:processApplicationManifestDebugForBundle",
                    ":feature2:processDebugMainManifest",
                    ":feature2:processDebugManifest",
                    ":feature2:processDebugManifestForPackage",
                    ":feature2:processManifestDebugForFeature"
                ),
                /*
                 * Tasks that should be cacheable but are not yet cacheable.
                 *
                 * If you add a task to this list, remember to file a bug for it.
                 */
                DID_WORK to setOf(
                    ":app:mergeDebugNativeLibs", /* Intended. See bug 153088766 */
                    ":app:mergeDebugResources", /* Bug 141301405 */
                    ":app:packageDebug", /* Bug 74595859 */
                    ":app:processDebugResources", /* Bug 141301405 */
                    ":app:writeDebugModuleMetadata",

                    ":feature1:featureDebugWriter",
                    ":feature1:mergeDebugNativeLibs", /* Intended. See bug 153088766 */
                    ":feature1:mergeDebugResources",
                    ":feature1:packageDebug",
                    ":feature1:processDebugResources",

                    ":feature2:featureDebugWriter",
                    ":feature2:mergeDebugNativeLibs", /* Intended. See bug 153088766 */
                    ":feature2:mergeDebugResources",
                    ":feature2:packageDebug",
                    ":feature2:processDebugResources"
                ),
                SKIPPED to setOf(
                    ":app:assembleDebug",
                    ":app:compileDebugAidl",
                    ":app:compileDebugRenderscript",
                    ":app:compileDebugShaders",
                    ":app:compileDebugSources",
                    ":app:mergeDebugNativeDebugMetadata",
                    ":app:processDebugJavaRes",
                    ":app:stripDebugDebugSymbols",

                    ":feature1:assembleDebug",
                    ":feature1:compileDebugAidl",
                    ":feature1:compileDebugRenderscript",
                    ":feature1:compileDebugShaders",
                    ":feature1:processDebugJavaRes",
                    ":feature1:stripDebugDebugSymbols",

                    ":feature2:assembleDebug",
                    ":feature2:compileDebugAidl",
                    ":feature2:compileDebugRenderscript",
                    ":feature2:compileDebugShaders",
                    ":feature2:processDebugJavaRes",
                    ":feature2:stripDebugDebugSymbols"
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
        return GradleTestProject
            .builder()
            .withName(projectName)
            .fromTestProject("dynamicApp")
            .create()
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
                "assembleDebug"
            )
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
