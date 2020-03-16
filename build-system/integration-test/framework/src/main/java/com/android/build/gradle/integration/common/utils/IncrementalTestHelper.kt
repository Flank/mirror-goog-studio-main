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
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/** Utility to write tests for incremental tasks. */
class IncrementalTestHelper(
    private val project: GradleTestProject,
    private val cleanTask: String = "clean",
    private val buildTask: String = "assembleDebug",
    private val filesToTrackChanges: Set<File> = emptySet()
) {

    // Record the timestamps and contents of files in the first (full) build
    private lateinit var fileTimestamps: Map<File, FileTime>
    private lateinit var fileContents: Map<File, ByteArray>

    // Record files with changed timestamps or contents in the second (incremental) build
    private lateinit var filesWithChangedTimestamps: Set<File>
    private lateinit var filesWithChangedContents: Set<File>

    private var executorSetter: ((GradleTaskExecutor) -> GradleTaskExecutor)? = null

    /**
     * Sets a custom executor to run tasks by providing a lambda that replaces the default executor
     * (project.executor()) with another one.
     *
     * @param executorSetter a lambda that takes the default executor and returns another one that
     *     replaces it
     */
    fun useCustomExecutor(executorSetter: (GradleTaskExecutor) -> GradleTaskExecutor):
            IncrementalTestHelper {
        this.executorSetter = executorSetter
        return this
    }

    /** Runs a full build. */
    fun runFullBuild(): IncrementalTestHelperStage2 {
        project.executor()
            .run(executorSetter ?: { it })
            .run(cleanTask, buildTask)

        // Record timestamps and contents
        val timestamps = mutableMapOf<File, FileTime>()
        val contents = mutableMapOf<File, ByteArray>()
        for (file in filesToTrackChanges) {
            check(file.isFile) { "File `${file.path}` does not exist or is a directory." }

            // Use Files.getLastModifiedTime instead of File.lastModified to prevent flakiness of
            // timestamps, according to the discussion at
            // https://android-review.googlesource.com/c/platform/frameworks/support/+/932356/13/lifecycle/integration-tests/gradletest/src/test/kotlin/androidx/lifecycle/IncrementalAnnotationProcessingTest.kt#110
            timestamps[file] = Files.getLastModifiedTime(file.toPath())
            contents[file] = file.readBytes()
        }
        fileTimestamps = timestamps.toMap()
        fileContents = contents.toMap()

        return IncrementalTestHelperStage2(this)
    }

    class IncrementalTestHelperStage2(
        private val incrementalTestHelper: IncrementalTestHelper
    ) {

        /** Applies a change. */
        fun applyChange(change: () -> Unit): IncrementalTestHelperStage3 {
            change()
            return IncrementalTestHelperStage3(incrementalTestHelper)
        }
    }

    class IncrementalTestHelperStage3(
        private val incrementalTestHelper: IncrementalTestHelper
    ) {

        /** Runs an incremental build. */
        fun runIncrementalBuild(): IncrementalTestHelperStage4 {
            with(incrementalTestHelper) {
                val result = project.executor()
                    .run(executorSetter ?: { it })
                    .run(buildTask)

                // Record changed files
                filesWithChangedTimestamps = fileTimestamps.filter { (file, previousTimestamp) ->
                    check(file.isFile) { "File `${file.path}` does not exist or is a directory." }
                    Files.getLastModifiedTime(file.toPath()) != previousTimestamp
                }.keys
                filesWithChangedContents = fileContents.filter { (file, previousContents) ->
                    check(file.isFile) { "File `${file.path}` does not exist or is a directory." }
                    !file.readBytes().contentEquals(previousContents)
                }.keys

                return IncrementalTestHelperStage4(incrementalTestHelper, result.taskStates)
            }
        }
    }

    class IncrementalTestHelperStage4(
        private val incrementalTestHelper: IncrementalTestHelper,
        private val taskStates: Map<String, TaskStateList.ExecutionState>
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
        ): IncrementalTestHelperStage4 {
            TaskStateAssertionHelper(taskStates).assertTaskStates(expectedTaskStates, exhaustive)
            return this
        }

        /** Asserts file changes. */
        fun assertFileChanges(fileChanges: Map<File, ChangeType>): IncrementalTestHelperStage4 {
            return assertFileChanges(
                fileChanges.filterValues { it == ChangeType.CHANGED }.keys,
                fileChanges.filterValues { it == ChangeType.CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS }.keys,
                fileChanges.filterValues { it == ChangeType.UNCHANGED }.keys
            )
        }

        /** Asserts file changes. */
        private fun assertFileChanges(
            filesWithChangedTimestampsAndContents: Set<File>,
            filesWithChangedTimestampsButNotContents: Set<File>,
            filesWithUnchangedTimestampsAndContents: Set<File>
        ): IncrementalTestHelperStage4 {
            (filesWithChangedTimestampsAndContents
                    + filesWithChangedTimestampsButNotContents
                    + filesWithUnchangedTimestampsAndContents).subtract(
                incrementalTestHelper.filesToTrackChanges
            ).let {
                check(it.isEmpty()) {
                    "The following files are missing from the set of files to track:\n" +
                            it.joinToString("\n")
                }
            }

            val actualFilesWithChangedTimestampsAndContents =
                incrementalTestHelper.filesWithChangedTimestamps
                    .intersect(incrementalTestHelper.filesWithChangedContents)
            val actualFilesWithChangedTimestampsButNotContents =
                incrementalTestHelper.filesWithChangedTimestamps
                    .subtract(incrementalTestHelper.filesWithChangedContents)
            val actualFilesWithUnchangedTimestampsAndContents =
                incrementalTestHelper.filesToTrackChanges.subtract(
                    incrementalTestHelper.filesWithChangedTimestamps.plus(
                        incrementalTestHelper.filesWithChangedContents
                    )
                )

            val actualFilesWithChangedContentsButNotTimestamps =
                incrementalTestHelper.filesWithChangedContents
                    .subtract(incrementalTestHelper.filesWithChangedTimestamps)
            check(actualFilesWithChangedContentsButNotTimestamps.isEmpty()) {
                "The following files have changed contents but unchanged timestamps," +
                        " which can cause tests to be flaky:\n" +
                        actualFilesWithChangedContentsButNotTimestamps.joinToString("\n") +
                        "To work around this, introduce a few milliseconds of sleep between builds."
            }

            filesWithChangedTimestampsAndContents
                .subtract(actualFilesWithChangedTimestampsAndContents).let {
                    assert(it.isEmpty()) {
                        "The following files are expected to have changed timestamps and contents," +
                                " but their contents have not changed:\n" +
                                it.joinToString("\n")
                    }
                }

            filesWithChangedTimestampsButNotContents
                .subtract(actualFilesWithChangedTimestampsButNotContents).let {
                    assert(it.isEmpty()) {
                        "The following files are expected to have changed timestamps and unchanged contents," +
                                " but either their timestamps have not changed or their contents have changed:\n" +
                                it.joinToString("\n")
                    }
                }

            filesWithUnchangedTimestampsAndContents
                .subtract(actualFilesWithUnchangedTimestampsAndContents).let {
                    assert(it.isEmpty()) {
                        "The following files are expected to have unchanged timestamps and contents," +
                                " but their timestamps have changed:\n" +
                                it.joinToString("\n")
                    }
                }

            return this
        }
    }
}

/** Type of a file change. */
enum class ChangeType {

    /** Changed timestamp and contents. */
    CHANGED,

    /** Changed timestamp but not contents. */
    CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS,

    /** Unchanged timestamp and contents. */
    UNCHANGED
}