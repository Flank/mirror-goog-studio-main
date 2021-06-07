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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TaskStateList
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration test to ensure cacheability when data binding is used
 * (https://issuetracker.google.com/69243050).
 */
@RunWith(FilterableParameterized::class)
class DataBindingCachingTest(private val withKotlin: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "withKotlin_{0}")
        @JvmStatic
        fun parameters() = listOf(
            arrayOf(true),
            arrayOf(false)
        )
    }

    /**
     * The expected states of tasks when running a second build with the Gradle build cache
     * enabled from an identical project at a different location.
     */
    private val expectedTaskStates: Map<String, TaskStateList.ExecutionState> = mapOf(
        // Sort by alphabetical order for easier searching
        ":checkDebugAarMetadata" to FROM_CACHE,
        ":compileDebugAidl" to SKIPPED,
        ":compileDebugJavaWithJavac" to FROM_CACHE,
        ":compileDebugRenderscript" to SKIPPED,
        ":createDebugCompatibleScreenManifests" to DID_WORK,
        ":dataBindingGenBaseClassesDebug" to FROM_CACHE,
        ":dataBindingMergeDependencyArtifactsDebug" to FROM_CACHE,
        ":dataBindingMergeGenClassesDebug" to FROM_CACHE,
        ":dataBindingTriggerDebug" to FROM_CACHE,
        ":extractDeepLinksDebug" to FROM_CACHE,
        ":generateDebugBuildConfig" to FROM_CACHE,
        ":generateDebugResources" to UP_TO_DATE,
        ":generateDebugResValues" to FROM_CACHE,
        ":javaPreCompileDebug" to FROM_CACHE,
        ":mergeDebugResources" to DID_WORK, /* Bug 141301405 */
        ":preBuild" to UP_TO_DATE,
        ":preDebugBuild" to UP_TO_DATE,
        ":processDebugMainManifest" to DID_WORK,
        ":processDebugManifest" to DID_WORK,
        ":processDebugManifestForPackage" to FROM_CACHE,
        ":processDebugResources" to DID_WORK
    ).plus(
        if (withKotlin) {
            mapOf(
                ":clean" to DID_WORK,
                // Kotlin tasks are not FROM_CACHE because they includes the project name in the
                // cache key, and the two project names in this test are currently different.
                ":compileDebugKotlin" to DID_WORK,
                ":kaptDebugKotlin" to FROM_CACHE,
                ":kaptGenerateStubsDebugKotlin" to DID_WORK
            )
        } else {
            mapOf(
                ":clean" to UP_TO_DATE
            )
        }
    ).plus(
            if (BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP.defaultValue) {
                mapOf(":mapDebugSourceSetPaths" to FROM_CACHE)
            } else {
                emptyMap()
            }
    ).plus(
            if (BooleanOption.GENERATE_MANIFEST_CLASS.defaultValue) {
                mapOf(":generateDebugManifestClass" to FROM_CACHE)
            } else {
                emptyMap()
            }
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("databinding")
        .withKotlinGradlePlugin(withKotlin)
        .withName("project")
        .create()

    @get:Rule
    val projectCopy = GradleTestProject.builder()
        .fromTestProject("databinding")
        .withKotlinGradlePlugin(withKotlin)
        .withName("projectCopy")
        .create()

    @get:Rule
    val buildCacheDirRoot = TemporaryFolder()

    @Before
    fun setUp() {
        if (withKotlin) {
            for (project in listOf(project, projectCopy)) {
                TestFileUtils.searchAndReplace(
                    project.buildFile,
                    "apply plugin: 'com.android.application'",
                    "apply plugin: 'com.android.application'\n" +
                            "apply plugin: 'kotlin-android'\n" +
                            "apply plugin: 'kotlin-kapt'"
                )
            }
        }
    }

    @Test
    fun `test main resources located within root project directory, expect cacheable tasks`() {
        CacheabilityTestHelper(project, projectCopy, buildCacheDirRoot.newFolder("build-cache"))
            .runTasks("clean", ":compileDebugJavaWithJavac")
            .assertTaskStates(expectedTaskStates, exhaustive = true)
    }

    @Test
    fun `test different package names generate different DataBindingInfo classes`() {
        projectCopy.file("src/main/AndroidManifest.xml").let {
            val manifest = it.readText()
            it.writeText(
                manifest.replace(
                    "package=\"android.databinding.testapp\"",
                    "package=\"android.databinding.testapp2\""
                )
            )
        }

        CacheabilityTestHelper(project, projectCopy, buildCacheDirRoot.newFolder("build-cache"))
            .runTasks("clean", ":dataBindingTriggerDebug")
            .assertTaskStates(mapOf(":dataBindingTriggerDebug" to DID_WORK))
    }


    @Test
    fun `test main resources located outside root project directory, expect non-cacheable tasks`() {
        // Add some resources outside of the root project directory
        for (project in listOf(project, projectCopy)) {
            val resDirOutsideProject = "../resOutside${project.name}"
            FileUtils.mkdirs(project.file("$resDirOutsideProject/layout"))
            FileUtils.copyFile(
                project.file("src/main/res/layout/activity_main.xml"),
                project.file("$resDirOutsideProject/layout/activity_main_copy.xml")
            )
            project.buildFile.appendText(
                """
                android.sourceSets.main {
                    res.srcDirs = res.srcDirs + ['$resDirOutsideProject']
                }
                """
            )
        }

        // Some tasks are no longer cacheable
        val updatedExpectedTaskStates = expectedTaskStates.toMutableMap()
        updatedExpectedTaskStates[":dataBindingGenBaseClassesDebug"] = DID_WORK
        if (withKotlin) {
            updatedExpectedTaskStates[":kaptDebugKotlin"] = DID_WORK
        } else {
            updatedExpectedTaskStates[":compileDebugJavaWithJavac"] = DID_WORK
        }

        CacheabilityTestHelper(project, projectCopy, buildCacheDirRoot.newFolder("build-cache"))
            .runTasks("clean", ":compileDebugJavaWithJavac")
            .assertTaskStates(updatedExpectedTaskStates, exhaustive = true)
    }
}
