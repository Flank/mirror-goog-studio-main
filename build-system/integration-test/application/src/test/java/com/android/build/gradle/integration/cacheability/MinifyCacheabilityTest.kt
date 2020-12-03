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
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.builder.model.CodeShrinker
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Similar to [CacheabilityTest], but targeting builds with `minifyEnabled=true` to verify a
 * different set of tasks.
 */
@RunWith(FilterableParameterized::class)
class MinifyCacheabilityTest (val shrinker: CodeShrinker) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "shrinker = {0}")
        fun testParameters(): Array<CodeShrinker> = arrayOf(CodeShrinker.PROGUARD, CodeShrinker.R8)

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
            ":mergeMinifiedJavaResource",
            ":mergeMinifiedJniLibFolders",
            ":mergeMinifiedShaders",
            ":processMinifiedManifestForPackage",
            ":validateSigningMinified",
            ":writeMinifiedAppMetadata",
            ":writeMinifiedSigningConfigVersions"
        ).plus(
            if (shrinker == CodeShrinker.R8) {
                setOf(":minifyMinifiedWithR8")
            } else {
                setOf(
                    ":mergeDexMinified",
                    ":minifyMinifiedWithProguard"
                )
            }
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
            ":mergeMinifiedResources",
            ":packageMinified",
            ":processMinifiedMainManifest",
            ":processMinifiedManifest",
            ":processMinifiedResources"
        ).plus(
            if (shrinker == CodeShrinker.PROGUARD) {
                setOf(":dexBuilderMinified")
            } else {
                emptySet()
            }
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
            ":stripMinifiedDebugSymbols"
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
            .useCustomExecutor {
                it.with(OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8, shrinker == CodeShrinker.R8)
            }
            .runTasks(
                "clean",
                "assembleMinified")
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
