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
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.options.StringOption
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth.assertThat
import java.io.File

class CacheabilityTestHelper private constructor(
    private val projectCopy1: GradleTestProject,
    private val projectCopy2: GradleTestProject) {

    private var buildCacheDir: File = File(projectCopy1.testDir.parent, "gradle-build-cache")

    private val taskResults =
        mapOf<TaskStateList.ExecutionState, MutableSet<String>>(
            UP_TO_DATE to mutableSetOf(),
            FROM_CACHE to mutableSetOf(),
            DID_WORK to mutableSetOf(),
            SKIPPED to mutableSetOf(),
            FAILED to mutableSetOf()
        )

    private var didExecute = false

    private val extraExecutorOperations: MutableList<(GradleTaskExecutor) -> GradleTaskExecutor> =
        mutableListOf()

    companion object {
        /**
         * Returns a CacheabilityTestHelper for two copies of a project
         */
        fun forProjects(projectCopy1: GradleTestProject, projectCopy2: GradleTestProject):
                CacheabilityTestHelper {
            return CacheabilityTestHelper(projectCopy1, projectCopy2)
        }
    }

    /**
     * Sets build cache directory
     */
    fun withBuildCacheDir(buildCacheDir: File): CacheabilityTestHelper {
        this.buildCacheDir = buildCacheDir

        setBuildCacheDirForProject(projectCopy1, buildCacheDir)
        setBuildCacheDirForProject(projectCopy2, buildCacheDir)

        return this
    }

    private fun setBuildCacheDirForProject(project: GradleTestProject, buildCacheDir: File) {
        val buildCacheString =
            """|buildCache {
            |    local {
            |        directory = "${buildCacheDir.path.replace("\\", "\\\\")}"
            |    }
            |}""".trimMargin("|")


        TestFileUtils.appendToFile(
            project.settingsFile,
            buildCacheString)
    }

    /**
     * Checks if a number of tasks are in UpToDate
     * @param tasks The list of tasks expected to be up to date
     * @param exclusive Whether the list of up to date tasks should contain ONLY the specified tasks
     */
    fun hasUpToDateTasks(tasks: Set<String>, exclusive: Boolean = false): CacheabilityTestHelper =
        hasTasks(UP_TO_DATE, tasks, "UpToDate Tasks", exclusive)

    /**
     * Checks if a number of tasks are in FromCache
     * @param tasks The list of tasks expected to be from cache
     * @param exclusive Whether the list of from cache tasks should contain ONLY the specified tasks
     */
    fun hasFromCacheTasks(tasks: Set<String>, exclusive: Boolean = false): CacheabilityTestHelper =
        hasTasks(FROM_CACHE, tasks, "FromCache Tasks", exclusive)

    /**
     * Checks if a number of tasks are in DidWork
     * @param tasks The list of tasks expected to have done work
     * @param exclusive Whether the list of did work tasks should contain ONLY the specified tasks
     */
    fun hasDidWorkTasks(tasks: Set<String>, exclusive: Boolean = false): CacheabilityTestHelper =
        hasTasks(DID_WORK, tasks, "DidWork Tasks", exclusive)

    /**
     * Checks if a number of tasks are in Skipped
     * @param tasks The list of tasks expected to have been skipped
     * @param exclusive Whether the list of skipped tasks should contain ONLY the specified tasks
     */
    fun hasSkippedTasks(tasks: Set<String>, exclusive: Boolean = false): CacheabilityTestHelper =
        hasTasks(SKIPPED, tasks, "Skipped Tasks", exclusive)

    /**
     * Checks if a number of tasks are in Failed
     * @param tasks The list of tasks expected to have failed
     * @param exclusive Whether the list of failed tasks should contain ONLY the specified tasks
     */
    fun hasFailedTasks(tasks: Set<String>, exclusive: Boolean = false): CacheabilityTestHelper =
        hasTasks(FAILED, tasks, "Failed Tasks", exclusive)

    private fun hasTasks(
        state: TaskStateList.ExecutionState,
        tasks: Set<String>,
        name: String?,
        exclusive: Boolean): CacheabilityTestHelper {

        assert(didExecute)

        if (exclusive) {
            if (name == null) {
                assertThat(taskResults.getValue(state))
                    .containsExactlyElementsIn(tasks)
            } else {
                assertThat(taskResults.getValue(state))
                    .named(name)
                    .containsExactlyElementsIn(tasks)
            }
        } else {
            if (name == null) {
                assertThat(taskResults.getValue(state))
                    .containsAtLeastElementsIn(tasks)
            } else {
                assertThat(taskResults.getValue(state))
                    .named(name)
                    .containsAtLeastElementsIn(tasks)
            }
        }
        return this
    }

    /**
     * Adds extra operations to be executed on the executor before running tasks
     * For instance, (operation1, operation2, operation3) will result in
     * operation1(executor), operation2(executor), operation3(executor) being run, with the
     * previous return value being passed to the next operation.
     * 
     * @param operations a series of lambdas describing extra operations.
     */
    fun withExecutorOperations(vararg operations: (GradleTaskExecutor) -> GradleTaskExecutor):
            CacheabilityTestHelper {
        extraExecutorOperations.addAll(operations)
        return this
    }

    /**
     * Runs the specified tasks on both projects
     */
    fun withTasks(vararg tasks: String): CacheabilityTestHelper {
        // Reset the result
        taskResults.getValue(UP_TO_DATE).clear()
        taskResults.getValue(FROM_CACHE).clear()
        taskResults.getValue(DID_WORK).clear()
        taskResults.getValue(SKIPPED).clear()
        taskResults.getValue(FAILED).clear()

        val chainExtraExecutorOperations = { it: GradleTaskExecutor ->
            /* Simulates chain calling for additional operations
             * similar to executor.operation1().operation2().operation3()...
             */
            var executor = it
            for (operation in extraExecutorOperations) {
                executor = operation(executor)
            }

            executor
        }

        // Build the first project
        projectCopy1
            .executor()
            .withArgument("--build-cache")
            .with(StringOption.BUILD_CACHE_DIR, buildCacheDir.toString())
            .run(chainExtraExecutorOperations)
            .run(tasks.asList())

        // Check that the build cache has been populated
        assertThat(buildCacheDir).exists()

        // Build the second project
        val result =
            projectCopy2
                .executor()
                .withArgument("--build-cache")
                .with(StringOption.BUILD_CACHE_DIR, buildCacheDir.toString())
                .run(chainExtraExecutorOperations)
                .run(tasks.asList())

        taskResults.getValue(UP_TO_DATE).addAll(result.upToDateTasks)
        taskResults.getValue(FROM_CACHE).addAll(result.fromCacheTasks)
        taskResults.getValue(DID_WORK).addAll(result.didWorkTasks)
        taskResults.getValue(SKIPPED).addAll(result.skippedTasks)
        taskResults.getValue(FAILED).addAll(result.failedTasks)

        didExecute = true

        return this
    }
}
