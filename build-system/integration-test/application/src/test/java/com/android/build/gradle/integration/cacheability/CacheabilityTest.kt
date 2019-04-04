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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Tests cacheability of tasks.
 *
 * See https://guides.gradle.org/using-build-cache/ for information on the Gradle build cache.
 */
@RunWith(JUnit4::class)
class CacheabilityTest {

    companion object {

        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"

        /**
         * The expected states of tasks when running a second build with the Gradle build cache
         * enabled from an identical project at a different location.
         */
        private val EXPECTED_TASK_STATES =
            mapOf(
                UP_TO_DATE to setOf(
                    ":app:clean",
                    ":app:preBuild",
                    ":app:generateDebugResources",
                    ":app:generateDebugAssets",
                    ":app:compileDebugSources",
                    ":app:preDebugBuild",
                    ":app:preDebugUnitTestBuild"
                ),
                FROM_CACHE to setOf(
                    ":app:generateDebugBuildConfig",
                    ":app:compileDebugShaders",
                    ":app:javaPreCompileDebug",
                    ":app:generateDebugResValues",
                    ":app:mergeDebugResources",
                    ":app:compileDebugJavaWithJavac",
                    ":app:checkDebugDuplicateClasses",
                    ":app:mergeDebugShaders",
                    ":app:mergeDebugAssets",
                    ":app:mergeExtDexDebug",
                    ":app:mergeDebugJniLibFolders",
                    ":app:processDebugManifest",
                    ":app:processDebugResources",
                    ":app:mainApkListPersistenceDebug",
                    ":app:validateSigningDebug",
                    ":app:signingConfigWriterDebug",
                    ":app:createDebugCompatibleScreenManifests",
                    ":app:javaPreCompileDebugUnitTest",
                    ":app:generateDebugUnitTestConfig",
                    ":app:compileDebugUnitTestJavaWithJavac",
                    ":app:packageDebugUnitTestForUnitTest",
                    ":app:testDebugUnitTest",
                    ":app:mergeDebugJavaResource",
                    ":app:mergeDebugNativeLibs",
                    ":app:mergeDexDebug"
                ),
                /*
                 * Tasks that should be cacheable but are not yet cacheable.
                 *
                 * If you add a task to this list, remember to file a bug for it.
                 */
                DID_WORK to setOf(
                    ":app:checkDebugManifest" /* Bug 74595857 */,
                    ":app:transformClassesWithDexBuilderForDebug" /* Bug 74595921 */,
                    ":app:transformNativeLibsWithStripDebugSymbolForDebug" /* Bug 120414535 */,
                    ":app:packageDebug" /* Bug 74595859 */
                ),
                SKIPPED to setOf(
                    ":app:compileDebugAidl",
                    ":app:compileDebugRenderscript",
                    ":app:processDebugJavaRes",
                    ":app:assembleDebug",
                    ":app:processDebugUnitTestJavaRes"
                ),
                FAILED to setOf()
            )
    }

    @get:Rule
    var projectCopy1 = setUpTestProject("projectCopy1")

    @get:Rule
    var projectCopy2 = setUpTestProject("projectCopy2")

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return with(EmptyActivityProjectBuilder()) {
            this.projectName = projectName
            withUnitTest = true
            useGradleBuildCache = true
            gradleBuildCacheDir = File("../$GRADLE_BUILD_CACHE_DIR")
            build()
        }
    }

    @Before
    fun setUp() {
        for (project in listOf(projectCopy1, projectCopy2)) {
            // Set up the project such that we can check the cacheability of AndroidUnitTest task
            TestFileUtils.appendToFile(
                project.getSubproject(":app").buildFile,
                "android { testOptions { unitTests { includeAndroidResources = true } } }"
            )
            TestFileUtils.appendToFile(
                project.gradlePropertiesFile,
                "${BooleanOption.USE_RELATIVE_PATH_IN_TEST_CONFIG.propertyName}=true"
            )
        }
    }

    @get:Rule
    val expect: Expect = Expect.create()

    @Test
    fun testRelocatability() {
        // Build the first project
        val buildCacheDir = File(projectCopy1.testDir.parent, GRADLE_BUILD_CACHE_DIR)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
        projectCopy1.executor().withArgument("--build-cache")
            .run("clean", "assembleDebug", "testDebugUnitTest")

        // Check that the build cache has been populated
        assertThat(buildCacheDir).exists()

        // Build the second project
        val result =
            projectCopy2.executor().withArgument("--build-cache")
                .run("clean", "assembleDebug", "testDebugUnitTest")

        // When running this test with bazel, StripDebugSymbolTransform does not run as the NDK
        // directory is not available. We need to remove that task from the expected tasks' states.
        var expectedDidWorkTasks = EXPECTED_TASK_STATES[DID_WORK]!!
        if (result.findTask(":app:transformNativeLibsWithStripDebugSymbolForDebug") == null) {
            expectedDidWorkTasks =
                    expectedDidWorkTasks.minus(":app:transformNativeLibsWithStripDebugSymbolForDebug")
        }

        // Check that the tasks' states are as expected
        expect.that(result.upToDateTasks)
            .named("UpToDate Tasks")
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[UP_TO_DATE]!!)
        expect.that(result.fromCacheTasks)
            .named("FromCache Tasks")
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[FROM_CACHE]!!)
        expect.that(result.didWorkTasks)
            .named("DidWork Tasks")
            .containsExactlyElementsIn(expectedDidWorkTasks)
        expect.that(result.skippedTasks)
            .named("Skipped Tasks")
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[SKIPPED]!!)
        expect.that(result.failedTasks)
            .named("Failed Tasks")
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[FAILED]!!)

        // Clean up the cache
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }
}
