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
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.builder.model.CodeShrinker
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests cacheability of tasks.
 *
 * See https://guides.gradle.org/using-build-cache/ for information on the Gradle build cache.
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
        UP_TO_DATE to setOf(
            ":clean",
            ":compileMinifiedSources",
            ":generateMinifiedAssets",
            ":generateMinifiedResources",
            ":preBuild"
        ),
        FROM_CACHE to setOf(
            ":checkMinifiedDuplicateClasses",
            ":compileMinifiedJavaWithJavac",
            ":extractDeepLinksMinified",
            ":generateMinifiedBuildConfig",
            ":generateMinifiedResValues",
            ":jacocoMinified",
            ":javaPreCompileMinified",
            ":mergeMinifiedAssets",
            ":mergeMinifiedGeneratedProguardFiles",
            ":mergeMinifiedJavaResource",
            ":mergeMinifiedJniLibFolders",
            ":mergeMinifiedNativeLibs",
            ":mergeMinifiedShaders",
            if (shrinker == CodeShrinker.R8) ":minifyMinifiedWithR8"
            else ":minifyMinifiedWithProguard",
            ":validateSigningMinified"
        ),
        DID_WORK to setOf(
            ":createMinifiedCompatibleScreenManifests",
            ":extractProguardFiles",
            ":mergeMinifiedResources",
            ":packageMinified",
            ":processMinifiedManifest",
            ":processMinifiedResources"
        ),
        SKIPPED to setOf(
            ":assembleMinified",
            ":compileMinifiedAidl",
            ":compileMinifiedRenderscript",
            ":compileMinifiedShaders",
            ":preMinifiedBuild",
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

        CacheabilityTestHelper
            .forProjects(
                projectCopy1,
                projectCopy2)
            .withBuildCacheDir(buildCacheDir)
            .withExecutorOperations(
                { it.with(OptionalBooleanOption.ENABLE_R8, shrinker == CodeShrinker.R8) }
            )
            .withTasks(
                "clean",
                "assembleMinified")
            .hasUpToDateTasks(EXPECTED_TASK_STATES.getValue(UP_TO_DATE))
            .hasFromCacheTasks(EXPECTED_TASK_STATES.getValue(FROM_CACHE))
            .hasDidWorkTasks(EXPECTED_TASK_STATES.getValue(DID_WORK))
            .hasSkippedTasks(EXPECTED_TASK_STATES.getValue(SKIPPED))
            .hasFailedTasks(EXPECTED_TASK_STATES.getValue(FAILED))
    }
}
