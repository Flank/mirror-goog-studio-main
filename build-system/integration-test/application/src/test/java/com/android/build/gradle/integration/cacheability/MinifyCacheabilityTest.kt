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

package com.android.build.gradle.integration.cacheability

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Similar to [CacheabilityTest], but targeting builds with `minifyEnabled=true` to verify a
 * different set of tasks.
 */
class MinifyCacheabilityTest {

    companion object {
        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"
    }

    /**
     * The expected states of tasks when running a second build with the Gradle build cache
     * enabled from an identical project at a different location.
     */
    private val EXPECTED_TASK_STATES = mapOf(
        // Sort by alphabetical order for easier searching
        UP_TO_DATE to setOf(
            ":clean",
            ":compileMinifiedSources",
            ":generateMinifiedAssets",
            ":generateMinifiedResources",
            ":preBuild",
            ":preMinifiedBuild",
        ),
        FROM_CACHE to setOf(
            ":checkMinifiedAarMetadata",
            ":checkMinifiedDuplicateClasses",
            ":compileMinifiedJavaWithJavac",
            ":compressMinifiedAssets",
            ":extractDeepLinksMinified",
            ":generateMinifiedBuildConfig",
            ":generateMinifiedJacocoPropertiesFile",
            ":generateMinifiedResValues",
            ":jacocoMinified",
            ":javaPreCompileMinified",
            ":mergeMinifiedAssets",
            ":mergeMinifiedGeneratedProguardFiles",
            ":mergeMinifiedJniLibFolders",
            ":mergeMinifiedShaders",
            ":minifyMinifiedWithR8",
            ":processMinifiedManifestForPackage",
            ":validateSigningMinified",
            ":writeMinifiedAppMetadata",
            ":writeMinifiedSigningConfigVersions"

        ).plus(
            if (BooleanOption.GENERATE_MANIFEST_CLASS.defaultValue) {
                setOf(":generateMinifiedManifestClass")
            } else {
                emptySet()
            }
        ),
        DID_WORK to setOf(
            ":createMinifiedCompatibleScreenManifests",
            ":extractProguardFiles",
            ":mergeMinifiedJavaResource", /* Bug 181142260 */
            ":mergeMinifiedResources",
            ":packageMinified",
            ":processMinifiedMainManifest",
            ":processMinifiedManifest",
            ":processMinifiedResources"
        ).plus(
                if (BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP.defaultValue) {
                    setOf(":mapMinifiedSourceSetPaths")
                } else {
                    emptySet()
                }
        ),
        SKIPPED to setOf(
            ":assembleMinified",
            ":compileMinifiedAidl",
            ":compileMinifiedRenderscript",
            ":compileMinifiedShaders",
            ":mergeMinifiedNativeDebugMetadata",
            ":mergeMinifiedNativeLibs",
            ":processMinifiedJavaRes",
            ":stripMinifiedDebugSymbols",
        ),
        FAILED to setOf()
    )

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
            .fromTestProject("minify")
            .create()
    }

    @Test
    fun testRelocatability() {
        val buildCacheDir = buildCacheDirRoot.root.resolve(GRADLE_BUILD_CACHE_DIR)

        CacheabilityTestHelper(projectCopy1, projectCopy2, buildCacheDir)
            .runTasks(
                "clean",
                "assembleMinified")
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
