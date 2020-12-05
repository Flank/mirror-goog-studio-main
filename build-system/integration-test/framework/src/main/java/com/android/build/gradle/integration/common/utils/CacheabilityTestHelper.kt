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

package com.android.build.gradle.integration.common.utils

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.TaskStateList
import com.android.testutils.truth.PathSubject.assertThat
import java.io.File

/** Utility to write tests for cacheable tasks. */
class CacheabilityTestHelper(
    private val projectCopy1: GradleTestProject,
    private val projectCopy2: GradleTestProject,
    private val buildCacheDir: File
) {

    private var executorSetter: ((GradleTaskExecutor) -> GradleTaskExecutor)? = null

    /**
     * Sets a custom executor to run tasks by providing a lambda that replaces the default executor
     * (project.executor()) with another one.
     *
     * @param executorSetter a lambda that takes the default executor and returns another one that
     *     replaces it
     */
    fun useCustomExecutor(executorSetter: (GradleTaskExecutor) -> GradleTaskExecutor):
            CacheabilityTestHelper {
        this.executorSetter = executorSetter
        return this
    }

    /** Runs the specified tasks on both projects. */
    fun runTasks(vararg tasks: String): CacheabilityTestHelperAssertionStage {
        // Run tasks on the first project
        setBuildCacheDirForProject(projectCopy1, buildCacheDir)
        projectCopy1
            .executor()
            .withArgument("--build-cache")
            .run(executorSetter ?: { it })
            .run(tasks.asList())

        // Check that the build cache has been populated
        assertThat(buildCacheDir).exists()

        // Run tasks on the second project
        setBuildCacheDirForProject(projectCopy2, buildCacheDir)
        val result =
            projectCopy2
                .executor()
                .withArgument("--build-cache")
                .run(executorSetter ?: { it })
                .run(tasks.asList())

        return CacheabilityTestHelperAssertionStage(result.taskStates)
    }

    private fun setBuildCacheDirForProject(project: GradleTestProject, buildCacheDir: File) {
        val buildCacheString =
            """|buildCache {
            |    local {
            |        directory = "${buildCacheDir.path.replace("\\", "\\\\")}"
            |    }
            |}""".trimMargin("|")

        TestFileUtils.appendToFile(project.settingsFile, buildCacheString)
    }

    class CacheabilityTestHelperAssertionStage(
        private val actualTaskStates: Map<String, TaskStateList.ExecutionState>
    ) {

        /**
         * Checks if the actual task states match the given expected task states.
         *
         * @param expectedTaskStates the expected task states
         * @param exhaustive whether the list of expected tasks is exhaustive (whether the number of
         *     expected tasks must equal the number of actual tasks)
         */
        fun assertTaskStates(
            expectedTaskStates: Map<String, TaskStateList.ExecutionState>,
            exhaustive: Boolean = false
        ): CacheabilityTestHelperAssertionStage {
            TaskStateAssertionHelper(actualTaskStates)
                .assertTaskStates(expectedTaskStates, exhaustive)
            return this
        }

        /**
         * Checks if the actual task states match the given expected task states.
         *
         * @param expectedTaskStates the expected task states
         * @param exhaustive whether the list of expected tasks is exhaustive (whether the number of
         *     expected tasks must equal the number of actual tasks)
         */
        fun assertTaskStatesByGroups(
            expectedTaskStates: Map<TaskStateList.ExecutionState, Set<String>>,
            exhaustive: Boolean = false
        ): CacheabilityTestHelperAssertionStage {
            TaskStateAssertionHelper(actualTaskStates)
                .assertTaskStatesByGroups(expectedTaskStates, exhaustive)
            return this
        }
    }
}
