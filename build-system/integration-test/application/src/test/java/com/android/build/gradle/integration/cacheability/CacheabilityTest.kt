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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TaskStateList
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
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
 * Verifies tasks' states in a cached clean build: Tasks should get their outputs from the build
 * cache, except those that should not be executed or are not intended to be cacheable (e.g., if
 * they run faster without using the build cache).
 */
class CacheabilityTest {


    /**
     * The expected states of tasks when running a second build with the Gradle build cache
     * enabled from an identical project at a different location.
     */
    private val expectedTaskStates: List<TaskInfo> = listOf(
            // Sort alphabetically for readability
            TaskInfo(FROM_CACHE, "bundle", "Classes", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "check", "AarMetadata", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "check", "DuplicateClasses", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "compile", "JavaWithJavac",
                    listOf("Debug", "DebugUnitTest", "Release", "ReleaseUnitTest")),
            TaskInfo(FROM_CACHE, "compress", "Assets", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "create", "CompatibleScreenManifests",
                    listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "desugar", "FileDependencies", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "dexBuilder", "", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "extractDeepLinks", "", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "generate", "BuildConfig", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "generate", "JacocoPropertiesFile", listOf("Debug")),
            TaskInfo(FROM_CACHE, "generate", "ResValues", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "generate", "Config", listOf("DebugUnitTest", "ReleaseUnitTest")),
            TaskInfo(FROM_CACHE, "jacoco", "", listOf("Debug")),
            TaskInfo(FROM_CACHE, "javaPreCompile", "",
                    listOf("Debug", "DebugUnitTest", "Release", "ReleaseUnitTest")),
            TaskInfo(FROM_CACHE, "merge", "Assets", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "merge", "JniLibFolders", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "merge", "Shaders", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "mergeDex", "", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "mergeExtDex", "", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "optimize", "Resources", listOf("Release")),
            TaskInfo(FROM_CACHE, "package", "ForUnitTest",
                    listOf("DebugUnitTest", "ReleaseUnitTest")),
            TaskInfo(FROM_CACHE, "parse", "IntegrityConfig", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "process", "MainManifest", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "process", "Manifest", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "process", "ManifestForPackage", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "test", "", listOf("DebugUnitTest", "ReleaseUnitTest")),
            TaskInfo(FROM_CACHE, "validateSigning", "", listOf("Debug")),
            TaskInfo(FROM_CACHE, "write", "AppMetadata", listOf("Debug", "Release")),
            TaskInfo(FROM_CACHE, "write", "SigningConfigVersions", listOf("Debug", "Release")),

            /*
             * The following tasks are either not yet cacheable, or not intended to be cacheable
             * (e.g., if they run faster without using the build cache).
             *
             * If you add a task to this list, remember to add an explanation/file a bug for it.
             */
            TaskInfo(DID_WORK, "collect", "Dependencies", listOf("Release")),
            TaskInfo(DID_WORK, "extractProguardFiles", "", listOf("Release"), isGlobalTask = true),
            TaskInfo(DID_WORK, "lintVital", "", listOf("Release")),
            /* Bug 178810169 */
            TaskInfo(DID_WORK, "lintVitalAnalyze", "", listOf("Release")),
            /* b/181142260 */
            TaskInfo(DID_WORK, "merge", "JavaResource", listOf("Debug", "Release")),
            /* Bug 141301405 */
            TaskInfo(DID_WORK, "merge", "Resources", listOf("Debug", "Release")),
            /* Bug 74595859 */
            TaskInfo(DID_WORK, "package", "", listOf("Debug", "Release")),
            TaskInfo(DID_WORK, "sdk", "DependencyData", listOf("Release")),

            TaskInfo(UP_TO_DATE, "clean", ""),
            TaskInfo(UP_TO_DATE,"generate", "Assets", listOf("Debug", "Release")),
            TaskInfo(UP_TO_DATE,"generate", "Resources", listOf("Debug", "Release")),
            TaskInfo(UP_TO_DATE,"pre", "Build",
                    listOf("", "Debug", "DebugUnitTest", "Release", "ReleaseUnitTest")),

            TaskInfo(SKIPPED, "assemble", "", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "compile", "Aidl", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "compile", "Renderscript", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "compile", "Shaders", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "compile", "Sources", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "extract", "NativeSymbolTables", listOf("Release")),
            TaskInfo(SKIPPED, "merge", "NativeDebugMetadata", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "merge", "NativeLibs", listOf("Debug", "Release")),
            TaskInfo(SKIPPED, "process", "JavaRes",
                    listOf("Debug", "DebugUnitTest","Release", "ReleaseUnitTest")),
            TaskInfo(SKIPPED, "strip", "DebugSymbols", listOf("Debug", "Release"))
    ).plus(
            // mapDebugSourceSetPaths is not cacheable but exists to enable the main
            // resource compilation to be cacheable.
            if (BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP.defaultValue) {
                listOf(
                        TaskInfo(FROM_CACHE, "process", "Resources", listOf("Debug", "Release")),
                        TaskInfo(DID_WORK, "map", "SourceSetPaths", listOf("Debug", "Release"))
                )
            } else {
                /* Bug 141301405 */
                listOf(
                        TaskInfo(DID_WORK,"process", "Resources", listOf("Debug", "Release"))
                )
            }
    ).plus(
        if (BooleanOption.GENERATE_MANIFEST_CLASS.defaultValue) {
            listOf(
                TaskInfo(FROM_CACHE, "generate", "ManifestClass", listOf("Debug", "Release"))
            )
        } else {
            emptyList()
        }
    )

    @get:Rule
    val buildCacheDir = TemporaryFolder()

    @get:Rule
    val projectCopy1 = setUpTestProject("projectCopy1")

    @get:Rule
    val projectCopy2 = setUpTestProject("projectCopy2")

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return with(EmptyActivityProjectBuilder()) {
            this.projectName = projectName
            this.withUnitTest = true
            // http://b/146208910 & http://b/149978740
            this.withConfigurationCaching = BaseGradleExecutor.ConfigurationCaching.OFF
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
            TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                "android { buildTypes { debug { testCoverageEnabled = true } } }"
            )
        }
    }

    @Test
    fun `check debug task states`() {
        checkTaskStates("Debug")
    }

    @Test
    fun `check release task states`() {
        checkTaskStates("Release")
    }

    private fun checkTaskStates(variant: String) {
        val expectedVariantTaskStatesMap = expectedTaskStatesMap {
            it.contains(variant) || it.isBlank()
        }
        CacheabilityTestHelper(projectCopy1, projectCopy2, buildCacheDir.root)
                .runTasks(
                        "clean",
                        "assemble$variant",
                        "test${variant}UnitTest",
                        ":app:parse${variant}IntegrityConfig"
                )
                .assertTaskStates(expectedVariantTaskStatesMap, exhaustive = true)
    }

    private class TaskInfo(
            private val executionState: TaskStateList.ExecutionState,
            private val taskPrefix: String,
            private val taskSuffix: String,
            private val variants: List<String> = emptyList(),
            private val isGlobalTask: Boolean = false
    ) {
        fun getVariantTaskMap(variantFilter: (variantName: String) -> Boolean) =
                getVariantTaskStrings(variantFilter).associateWith { executionState }

        private fun getVariantTaskStrings(
                variantFilter: (variantName: String) -> Boolean) : List<String> =
                if (variants.any()) {
                    variants
                            .filter(variantFilter)
                            .map { variant ->
                                ":app:$taskPrefix${ if(isGlobalTask) "" else variant }$taskSuffix"
                            }
                } else {
                    listOf(":app:$taskPrefix$taskSuffix")
                }
    }

    private fun expectedTaskStatesMap(variantFilter: (variantName: String) -> Boolean)
            : Map<String, TaskStateList.ExecutionState> {
        return mutableMapOf<String, TaskStateList.ExecutionState>().apply {
            for (taskInfo in expectedTaskStates) {
                putAll(taskInfo.getVariantTaskMap(variantFilter))
            }
        }
    }
}
