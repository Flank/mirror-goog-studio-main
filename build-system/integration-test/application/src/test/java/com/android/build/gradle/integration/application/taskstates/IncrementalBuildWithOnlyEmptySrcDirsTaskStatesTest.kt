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
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Verifies tasks' states after an incremental build where the only changes are new (empty) source
 * directories. All tasks should be UP_TO_DATE or SKIPPED. No tasks should be executed.
 */
class IncrementalBuildWithOnlyEmptySrcDirsTaskStatesTest {

    companion object {

        private val EXPECTED_TASK_STATES =
            mapOf(
                // Sort alphabetically for readability
                DID_WORK to emptySet(),
                UP_TO_DATE to
                    setOf(
                            ":app:assembleDebug",
                            ":app:checkDebugAarMetadata",
                            ":app:checkDebugDuplicateClasses",
                            ":app:compileDebugJavaWithJavac",
                            ":app:compressDebugAssets",
                            ":app:createDebugApkListingFileRedirect",
                            ":app:createDebugCompatibleScreenManifests",
                            ":app:desugarDebugFileDependencies",
                            ":app:dexBuilderDebug",
                            ":app:extractDeepLinksDebug",
                            ":lib:extractDeepLinksForAarDebug",
                            ":app:generateDebugAssets",
                            ":app:generateDebugBuildConfig",
                            ":app:generateDebugResources",
                            ":app:generateDebugResValues",
                            ":app:javaPreCompileDebug",
                            ":app:mapDebugSourceSetPaths",
                            ":app:mergeDebugAssets",
                            ":app:mergeDebugJavaResource",
                            ":app:mergeDebugJniLibFolders",
                            ":app:mergeDebugResources",
                            ":app:mergeDebugShaders",
                            ":app:mergeDexDebug",
                            ":app:mergeExtDexDebug",
                            ":app:packageDebug",
                            ":app:preBuild",
                            ":app:preDebugBuild",
                            ":app:processDebugMainManifest",
                            ":app:processDebugManifest",
                            ":app:processDebugManifestForPackage",
                            ":app:processDebugResources",
                            ":app:validateSigningDebug",
                            ":app:writeDebugAppMetadata",
                            ":app:writeDebugSigningConfigVersions",
                            ":lib:assembleDebug",
                            ":lib:bundleDebugAar",
                            ":lib:bundleLibCompileToJarDebug",
                            ":lib:bundleLibRuntimeToJarDebug",
                            ":lib:compileDebugJavaWithJavac",
                            ":lib:compileDebugLibraryResources",
                            ":lib:copyDebugJniLibsProjectAndLocalJars",
                            ":lib:copyDebugJniLibsProjectOnly",
                            ":lib:extractDebugAnnotations",
                            ":lib:extractDeepLinksDebug",
                            ":lib:generateDebugAssets",
                            ":lib:generateDebugBuildConfig",
                            ":lib:generateDebugRFile",
                            ":lib:generateDebugResValues",
                            ":lib:generateDebugResources",
                            ":lib:javaPreCompileDebug",
                            ":lib:mergeDebugConsumerProguardFiles",
                            ":lib:mergeDebugGeneratedProguardFiles",
                            ":lib:mergeDebugJavaResource",
                            ":lib:mergeDebugJniLibFolders",
                            ":lib:mergeDebugNativeLibs",
                            ":lib:mergeDebugShaders",
                            ":lib:packageDebugAssets",
                            ":lib:packageDebugRenderscript",
                            ":lib:packageDebugResources",
                            ":lib:parseDebugLocalResources",
                            ":lib:preBuild",
                            ":lib:preDebugBuild",
                            ":lib:prepareDebugArtProfile",
                            ":lib:prepareLintJarForPublish",
                            ":lib:processDebugManifest",
                            ":lib:syncDebugLibJars",
                            ":lib:writeDebugAarMetadata",
                        )
                        .plus(
                            if (BooleanOption.GENERATE_MANIFEST_CLASS.defaultValue) {
                                setOf(":app:generateDebugManifestClass")
                            } else {
                                emptySet()
                            }),
                SKIPPED to
                    setOf(
                        ":app:compileDebugAidl",
                        ":app:compileDebugRenderscript",
                        ":app:compileDebugShaders",
                        ":app:mergeDebugNativeDebugMetadata",
                        ":app:mergeDebugNativeLibs",
                        ":app:processDebugJavaRes",
                        ":app:stripDebugDebugSymbols",
                        ":lib:bundleLibResDebug",
                        ":lib:compileDebugAidl",
                        ":lib:compileDebugRenderscript",
                        ":lib:compileDebugShaders",
                        ":lib:mergeDebugNativeLibs",
                        ":lib:packageDebugRenderscript",
                        ":lib:processDebugJavaRes",
                        ":lib:stripDebugDebugSymbols"))
    }

    @get:Rule
    var project =
        EmptyActivityProjectBuilder()
            .addAndroidLibrary(subprojectName = "lib", addImplementationDependencyFromApp = true)
            .build()

    @Test
    fun `check task states`() {
        project.executor().run("assembleDebug")
        assertThat(project.mainSrcDir.resolve("emptyDir").mkdirs()).isTrue()
        assertThat(project.getSubproject("lib").mainSrcDir.resolve("emptyDir").mkdirs()).isTrue()
        val result = project.executor().run("assembleDebug")

        TaskStateAssertionHelper(result.taskStates)
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
