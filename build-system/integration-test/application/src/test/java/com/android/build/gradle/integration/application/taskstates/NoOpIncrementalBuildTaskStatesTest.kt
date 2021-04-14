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

package com.android.build.gradle.integration.application.taskstates

import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.TaskStateAssertionHelper
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

/**
 * Verifies tasks' states in a no-op incremental build: All tasks should be up-to-date (or skipped).
 *
 * (A no-op incremental build is a build that immediately follows a previous build without any
 * changes.)
 */
class NoOpIncrementalBuildTaskStatesTest {

    companion object {

        private val EXPECTED_TASK_STATES = mapOf(
            // Sort alphabetically for readability
            DID_WORK to emptySet(), // No tasks should run
            UP_TO_DATE to setOf(
                ":app:assembleDebug",
                ":app:checkDebugAarMetadata",
                ":app:generateDebugAssets",
                ":app:preBuild",
                ":app:preDebugBuild",
                ":app:preDebugUnitTestBuild",
                ":app:bundleDebugClasses",
                ":app:checkDebugDuplicateClasses",
                ":app:compileDebugJavaWithJavac",
                ":app:compileDebugSources",
                ":app:compileDebugUnitTestJavaWithJavac",
                ":app:compressDebugAssets",
                ":app:createDebugCompatibleScreenManifests",
                ":app:desugarDebugFileDependencies",
                ":app:dexBuilderDebug",
                ":app:extractDeepLinksDebug",
                ":app:generateDebugBuildConfig",
                ":app:generateDebugResources",
                ":app:generateDebugResValues",
                ":app:javaPreCompileDebug",
                ":app:javaPreCompileDebugUnitTest",
                ":app:mergeDebugAssets",
                ":app:mergeDebugJavaResource",
                ":app:mergeDebugJniLibFolders",
                ":app:mergeDebugResources",
                ":app:mergeDebugShaders",
                ":app:mergeDexDebug",
                ":app:mergeExtDexDebug",
                ":app:packageDebug",
                ":app:processDebugMainManifest",
                ":app:processDebugManifest",
                ":app:processDebugManifestForPackage",
                ":app:processDebugResources",
                ":app:testDebugUnitTest",
                ":app:validateSigningDebug",
                ":app:writeDebugAppMetadata",
                ":app:writeDebugSigningConfigVersions"
            ).plus(
                    if (BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP.defaultValue) {
                        setOf(":app:mapDebugSourceSetPaths")
                    } else {
                        emptySet()
                    }
            ).plus(
                if (BooleanOption.GENERATE_MANIFEST_CLASS.defaultValue) {
                    setOf(":app:generateDebugManifestClass")
                } else {
                    emptySet()
                }
            ),
            SKIPPED to setOf(
                ":app:compileDebugAidl",
                ":app:compileDebugRenderscript",
                ":app:compileDebugShaders",
                ":app:mergeDebugNativeDebugMetadata",
                ":app:mergeDebugNativeLibs",
                ":app:processDebugJavaRes",
                ":app:processDebugUnitTestJavaRes",
                ":app:stripDebugDebugSymbols",
            )
        )
    }

    @get:Rule
    var project = EmptyActivityProjectBuilder().also { it.withUnitTest = true }.build()

    @Test
    fun `check task states`() {
        val result = project.executor()
            .run {
                val tasks = listOf("assembleDebug", "testDebugUnitTest")
                run(tasks)
                run(tasks)
            }
        TaskStateAssertionHelper(result.taskStates)
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
