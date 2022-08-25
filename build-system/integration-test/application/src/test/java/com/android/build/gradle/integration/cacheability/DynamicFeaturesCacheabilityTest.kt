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
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Similar to [CacheabilityTest], but targeting projects using dynamic features to verify a
 * different set of tasks.
 */
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
                    ":feature1:generateDebugAssets",
                    ":feature1:generateDebugResources",
                    ":feature1:preBuild",
                    ":feature1:preDebugBuild",

                    ":feature2:clean",
                    ":feature2:generateDebugAssets",
                    ":feature2:generateDebugResources",
                    ":feature2:preBuild",
                    ":feature2:preDebugBuild"
                ),
                FROM_CACHE to setOf(
                    ":app:checkDebugLibraries",
                    ":app:compileDebugJavaWithJavac",
                    ":app:compressDebugAssets",
                    ":app:desugarDebugFileDependencies",
                    ":app:dexBuilderDebug",
                    ":app:extractDeepLinksDebug",
                    ":app:generateDebugBuildConfig",
                    ":app:generateDebugFeatureMetadata",
                    ":app:generateDebugFeatureTransitiveDeps",
                    ":app:generateDebugResValues",
                    ":app:javaPreCompileDebug",
                    ":app:lintAnalyzeDebug",
                    ":app:mergeDebugAssets",
                    ":app:mergeDebugJniLibFolders",
                    ":app:mergeDebugResources",
                    ":app:mergeDebugShaders",
                    ":app:mergeDexDebug",
                    ":app:processDebugMainManifest",
                    ":app:processDebugManifest",
                    ":app:processDebugManifestForPackage",
                    ":app:processDebugResources", /* Bug 141301405 */

                    ":feature1:compileDebugJavaWithJavac",
                    ":feature1:compressDebugAssets",
                    ":feature1:desugarDebugFileDependencies",
                    ":feature1:dexBuilderDebug",
                    ":feature1:extractDeepLinksDebug",
                    ":feature1:generateDebugBuildConfig",
                    ":feature1:generateDebugFeatureTransitiveDeps",
                    ":feature1:generateDebugResValues",
                    ":feature1:javaPreCompileDebug",
                    ":feature1:lintAnalyzeDebug",
                    ":feature1:mergeDebugAssets",
                    ":feature1:mergeDebugJniLibFolders",
                    ":feature1:mergeDebugResources",
                    ":feature1:mergeDebugShaders",
                    ":feature1:mergeExtDexDebug",
                    ":feature1:mergeLibDexDebug",
                    ":feature1:mergeProjectDexDebug",
                    ":feature1:processDebugMainManifest",
                    ":feature1:processDebugManifest",
                    ":feature1:processDebugManifestForPackage",
                    ":feature1:processDebugResources",
                    ":feature1:processManifestDebugForFeature",

                    ":feature2:checkDebugAarMetadata",
                    ":feature2:compileDebugJavaWithJavac",
                    ":feature2:compressDebugAssets",
                    ":feature2:desugarDebugFileDependencies",
                    ":feature2:dexBuilderDebug",
                    ":feature2:extractDeepLinksDebug",
                    ":feature2:generateDebugBuildConfig",
                    ":feature2:generateDebugFeatureTransitiveDeps",
                    ":feature2:generateDebugResValues",
                    ":feature2:javaPreCompileDebug",
                    ":feature2:lintAnalyzeDebug",
                    ":feature2:mergeDebugAssets",
                    ":feature2:mergeDebugJniLibFolders",
                    ":feature2:mergeDebugResources",
                    ":feature2:mergeDebugShaders",
                    ":feature2:mergeExtDexDebug",
                    ":feature2:mergeLibDexDebug",
                    ":feature2:mergeProjectDexDebug",
                    ":feature2:processDebugMainManifest",
                    ":feature2:processDebugManifest",
                    ":feature2:processDebugManifestForPackage",
                    ":feature2:processDebugResources",
                    ":feature2:processManifestDebugForFeature",
                ).plus(
                    if (BooleanOption.GENERATE_MANIFEST_CLASS.defaultValue) {
                        setOf(
                            ":app:generateDebugManifestClass",
                            ":feature1:generateDebugManifestClass",
                            ":feature2:generateDebugManifestClass"
                        )
                    } else {
                        emptySet()
                    }
                ),
                /*
                 * Tasks that should be cacheable but are not yet cacheable.
                 *
                 * If you add a task to this list, remember to file a bug for it.
                 */
                DID_WORK to setOf(
                    ":app:bundleDebugClassesToCompileJar", /** Intentionally not cacheable. See [com.android.build.gradle.internal.feature.BundleAllClasses] */
                    ":app:bundleDebugClassesToRuntimeJar", /** Intentionally not cacheable. See [com.android.build.gradle.internal.feature.BundleAllClasses] */
                    ":app:checkDebugAarMetadata", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.CheckAarMetadataTask] */
                    ":app:checkDebugDuplicateClasses", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask] */
                    ":app:createDebugApkListingFileRedirect",
                    ":app:createDebugCompatibleScreenManifests", /** Intentionally not cacheable. See [com.android.build.gradle.tasks.CompatibleScreensManifest] */
                    ":app:copyDebugAndroidLintReports", // intentionally not cacheable
                    ":app:extractProguardFiles", // intentionally not cacheable
                    ":app:generateDebugLintModel", // intentionally not cacheable
                    ":app:lintDebug", // intentionally not cacheable
                    ":app:lintReportDebug", // intentionally not cacheable
                    ":app:mapDebugSourceSetPaths", // intentionally not cacheable
                    ":app:mergeDebugJavaResource", /* Bug 181142260 */
                    ":app:packageDebug", /* Bug 74595859 */
                    ":app:preDebugBuild", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.AppPreBuildTask]*/
                    ":app:signingConfigWriterDebug", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.SigningConfigWriterTask]*/
                    ":app:validateSigningDebug", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.ValidateSigningTask] */
                    ":app:writeDebugModuleMetadata",
                    ":app:writeDebugAppMetadata", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.AppMetadataTask] */
                    ":app:writeDebugSigningConfigVersions", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask] */

                    ":feature1:checkDebugAarMetadata", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.CheckAarMetadataTask] */
                    ":feature1:checkDebugDuplicateClasses", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask] */
                    ":feature1:createDebugApkListingFileRedirect",
                    ":feature1:createDebugCompatibleScreenManifests", /** Intentionally not cacheable. See [com.android.build.gradle.tasks.CompatibleScreensManifest] */
                    ":feature1:extractProguardFiles", // intentionally not cacheable
                    ":feature1:featureDebugWriter",
                    ":feature1:generateDebugLintModel", // intentionally not cacheable
                    ":feature1:mapDebugSourceSetPaths", // intentionally not cacheable
                    ":feature1:mergeDebugJavaResource",
                    ":feature1:packageDebug",
                    ":feature1:processApplicationManifestDebugForBundle", /** Intentionally not cacheable. See [com.android.build.gradle.tasks.ProcessManifestForBundleTask] */

                    ":feature2:checkDebugAarMetadata", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.CheckAarMetadataTask] */
                    ":feature2:checkDebugDuplicateClasses", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask] */
                    ":feature2:createDebugApkListingFileRedirect",
                    ":feature2:createDebugCompatibleScreenManifests", /** Intentionally not cacheable. See [com.android.build.gradle.tasks.CompatibleScreensManifest] */
                    ":feature2:extractProguardFiles", // intentionally not cacheable
                    ":feature2:featureDebugWriter",
                    ":feature2:generateDebugLintModel", // intentionally not cacheable
                    ":feature2:mapDebugSourceSetPaths", // intentionally not cacheable
                    ":feature2:mergeDebugJavaResource",
                    ":feature2:packageDebug",
                    ":feature2:processApplicationManifestDebugForBundle", /** Intentionally not cacheable. See [com.android.build.gradle.tasks.ProcessManifestForBundleTask] */

                ),
                SKIPPED to setOf(
                    ":app:assembleDebug",
                    ":app:compileDebugAidl",
                    ":app:compileDebugRenderscript",
                    ":app:compileDebugShaders",
                    ":app:mergeDebugNativeDebugMetadata",
                    ":app:mergeDebugNativeLibs",
                    ":app:processDebugJavaRes",
                    ":app:stripDebugDebugSymbols",

                    ":feature1:assembleDebug",
                    ":feature1:compileDebugAidl",
                    ":feature1:compileDebugRenderscript",
                    ":feature1:compileDebugShaders",
                    ":feature1:mergeDebugNativeLibs",
                    ":feature1:processDebugJavaRes",
                    ":feature1:stripDebugDebugSymbols",

                    ":feature2:assembleDebug",
                    ":feature2:compileDebugAidl",
                    ":feature2:compileDebugRenderscript",
                    ":feature2:compileDebugShaders",
                    ":feature2:mergeDebugNativeLibs",
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
                "assembleDebug",
                "lintDebug"
            )
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
