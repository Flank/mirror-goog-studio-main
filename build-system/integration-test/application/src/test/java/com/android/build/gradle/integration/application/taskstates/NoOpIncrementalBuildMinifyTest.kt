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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.TaskStateAssertionHelper
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Similar to [NoOpIncrementalBuildTaskStatesTest], but targeting the release build type with
 * `minifyEnabled=true` to verify a different set of tasks.
 */
class NoOpIncrementalBuildMinifyTest {

    companion object {

        private val EXPECTED_TASK_STATES = mapOf(
            // Sort alphabetically for readability
            DID_WORK to setOf(
                // Lint declares no outputs, so it's never up-to-date. It's probably for the
                // better, because it's hard to declare all inputs (they include the SDK
                // and contents of the Google maven repo).
                ":lint"
            ),
            UP_TO_DATE to setOf(
                ":assembleDebugAndroidTest",
                ":assembleRelease",
                ":bundleDebugClasses",
                ":bundleReleaseClasses",
                ":checkDebugAarMetadata",
                ":checkDebugAndroidTestAarMetadata",
                ":checkDebugAndroidTestDuplicateClasses",
                ":checkReleaseAarMetadata",
                ":checkReleaseDuplicateClasses",
                ":collectReleaseDependencies",
                ":compileDebugAndroidTestJavaWithJavac",
                ":compileDebugJavaWithJavac",
                ":compileReleaseJavaWithJavac",
                ":compressDebugAndroidTestAssets",
                ":compressReleaseAssets",
                ":createDebugCompatibleScreenManifests",
                ":createReleaseCompatibleScreenManifests",
                ":desugarDebugAndroidTestFileDependencies",
                ":dexBuilderDebugAndroidTest",
                ":extractDeepLinksDebug",
                ":extractDeepLinksRelease",
                ":extractProguardFiles",
                ":generateDebugAndroidTestAssets",
                ":generateDebugAndroidTestBuildConfig",
                ":generateDebugAndroidTestResValues",
                ":generateDebugAndroidTestResources",
                ":generateDebugBuildConfig",
                ":generateDebugResValues",
                ":generateDebugResources",
                ":generateReleaseAssets",
                ":generateReleaseBuildConfig",
                ":generateReleaseResValues",
                ":generateReleaseResources",
                ":javaPreCompileDebug",
                ":javaPreCompileDebugAndroidTest",
                ":javaPreCompileRelease",
                ":javaPreCompileReleaseUnitTest",
                ":mergeDebugAndroidTestAssets",
                ":mergeDebugAndroidTestJavaResource",
                ":mergeDebugAndroidTestJniLibFolders",
                ":mergeDebugAndroidTestNativeLibs", /* Bug 154984238 */
                ":mergeDebugAndroidTestResources",
                ":mergeDebugAndroidTestShaders",
                ":mergeDebugResources",
                ":mergeDexDebugAndroidTest",
                ":mergeExtDexDebugAndroidTest",
                ":mergeReleaseAssets",
                ":mergeReleaseGeneratedProguardFiles",
                ":mergeReleaseJavaResource",
                ":mergeReleaseJniLibFolders",
                ":mergeReleaseResources",
                ":mergeReleaseShaders",
                ":minifyReleaseWithR8",
                ":packageDebugAndroidTest",
                ":packageRelease",
                ":preBuild",
                ":preDebugBuild",
                ":preReleaseBuild",
                ":preReleaseUnitTestBuild",
                ":processDebugAndroidTestManifest",
                ":processDebugAndroidTestResources",
                ":processDebugMainManifest",
                ":processDebugManifest",
                ":processDebugManifestForPackage",
                ":processDebugResources",
                ":processReleaseMainManifest",
                ":processReleaseManifest",
                ":processReleaseManifestForPackage",
                ":processReleaseResources",
                ":sdkReleaseDependencyData",
                ":validateSigningDebugAndroidTest"
            ),
            SKIPPED to setOf(
                ":compileDebugAidl",
                ":compileDebugAndroidTestAidl",
                ":compileDebugAndroidTestRenderscript",
                ":compileDebugAndroidTestShaders",
                ":compileDebugRenderscript",
                ":compileReleaseAidl",
                ":compileReleaseRenderscript",
                ":compileReleaseShaders",
                ":compileReleaseUnitTestJavaWithJavac",
                ":mergeReleaseNativeDebugMetadata",
                ":mergeReleaseNativeLibs",
                ":preDebugAndroidTestBuild",
                ":processDebugAndroidTestJavaRes",
                ":processReleaseJavaRes",
                ":processReleaseUnitTestJavaRes",
                ":stripReleaseDebugSymbols",
                ":testReleaseUnitTest"
            )
        )
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        // http://b/149978740 and http://b/146208910
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setUp() {
        project.buildFile.appendText(
            """
            android.buildTypes {
                release { minifyEnabled true }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `check task states`() {
        val result = project.executor().run {
            val tasks =
                listOf("assembleRelease", "testReleaseUnitTest", "assembleDebugAndroidTest", "lint")
            run(tasks)
            run(tasks)
        }
        TaskStateAssertionHelper(result.taskStates)
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
