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
    private val buildTasks: List<String>,
    private val filesOrDirsToTrackChanges: Set<File> = emptySet()
) {

    constructor(
        project: GradleTestProject,
        buildTask: String,
        filesOrDirsToTrackChanges: Set<File> = emptySet()
    ) : this(project, listOf(buildTask), filesOrDirsToTrackChanges)

    /** The files to track changes. (These are regular files, not directories.) */
    private lateinit var filesToTrackChanges: Set<File>

    // The timestamps and contents of the tracked files after the first (full) build.
    private lateinit var fileTimestamps: Map<File, FileTime>
    private lateinit var fileContents: Map<File, ByteArray>

    /** The changes of the tracked files after the second (incremental) build. */
    private lateinit var fileChanges: Map<File, ChangeType>

    private var executorUpdater: ((GradleTaskExecutor) -> Unit)? = null

    /**
     * Provides a callback to update the default executor (project.executor()) with custom
     * properties.
     */
    fun updateExecutor(executorUpdater: (GradleTaskExecutor) -> Unit): IncrementalTestHelper {
        this.executorUpdater = executorUpdater
        return this
    }

    /** Records the timestamps and contents of the tracked files. */
    private fun recordTimestampsAndContents() {
        filesToTrackChanges = filesOrDirsToTrackChanges.flatMap { fileOrDir ->
            if (fileOrDir.isDirectory) {
                fileOrDir.walk().filter { it.isFile }.toList()
            } else {
                listOf(fileOrDir)
            }
        }.toSet()

        val timestamps = mutableMapOf<File, FileTime>()
        val contents = mutableMapOf<File, ByteArray>()
        for (file in filesToTrackChanges) {
            check(file.exists()) { "File ${file.path} does not exist." }
            check(!file.isDirectory) { "File ${file.path} is a directory." }

            // Use Files.getLastModifiedTime instead of File.lastModified to prevent flakiness of
            // timestamps, according to the discussion at
            // https://android-review.googlesource.com/c/platform/frameworks/support/+/932356/13/lifecycle/integration-tests/gradletest/src/test/kotlin/androidx/lifecycle/IncrementalAnnotationProcessingTest.kt#110
            timestamps[file] = Files.getLastModifiedTime(file.toPath())
            contents[file] = file.readBytes()
        }
        fileTimestamps = timestamps.toMap()
        fileContents = contents.toMap()
    }

    /** Records the changes of the tracked files. */
    private fun recordChanges() {
        fileChanges = filesToTrackChanges.map { file ->
            check(file.exists()) { "File ${file.path} does not exist." }
            check(!file.isDirectory) { "File ${file.path} is a directory." }

            val timestampChanged =
                Files.getLastModifiedTime(file.toPath()) != checkNotNull(fileTimestamps[file])
            val contentsChanged = !file.readBytes().contentEquals(checkNotNull(fileContents[file]))

            file to (if (timestampChanged) {
                if (contentsChanged) {
                    ChangeType.CHANGED
                } else {
                    ChangeType.CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS
                }
            } else {
                if (contentsChanged) {
                    error(
                        "File ${file.path} has changed contents but unchanged timestamps, which can cause flaky tests.\n" +
                                "To work around this, introduce a few milliseconds of sleep between builds."
                    )
                } else {
                    ChangeType.UNCHANGED
                }
            })
        }.toMap()
    }

    /** Runs a full build. */
    fun runFullBuild(): IncrementalTestHelperAfterFullBuild {
        project.executor()
            .apply(executorUpdater ?: {})
            .run(listOf("clean") + buildTasks)

        recordTimestampsAndContents()

        return IncrementalTestHelperAfterFullBuild(this)
    }

    class IncrementalTestHelperAfterFullBuild(
        private val incrementalTestHelper: IncrementalTestHelper
    ) {

        /** Applies a change. */
        fun applyChange(change: () -> Unit): IncrementalTestHelperAfterIncrementalChange {
            change()
            return IncrementalTestHelperAfterIncrementalChange(incrementalTestHelper)
        }
    }

    class IncrementalTestHelperAfterIncrementalChange(
        private val incrementalTestHelper: IncrementalTestHelper
    ) {

        /** Runs an incremental build. */
        fun runIncrementalBuild(): IncrementalTestHelperAfterIncrementalBuild {
            with(incrementalTestHelper) {
                val result = project.executor()
                    .apply(executorUpdater ?: {})
                    .run(buildTasks)

                recordChanges()

                return IncrementalTestHelperAfterIncrementalBuild(
                    incrementalTestHelper,
                    result.taskStates
                )
            }
        }
    }

    class IncrementalTestHelperAfterIncrementalBuild(
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
        ): IncrementalTestHelperAfterIncrementalBuild {
            TaskStateAssertionHelper(taskStates).assertTaskStates(expectedTaskStates, exhaustive)
            return this
        }

        /** Asserts file changes. */
        fun assertFileChanges(expectedFileChanges: Map<File, ChangeType>):
                IncrementalTestHelperAfterIncrementalBuild {
            val assertionFailures = mutableListOf<String>()
            for ((file, expectedChangeType) in expectedFileChanges) {
                val actualChangeType = incrementalTestHelper.fileChanges[file]
                check(actualChangeType != null) {
                    "File ${file.path} is missing from the set of files to track."
                }
                if (actualChangeType != expectedChangeType) {
                    assertionFailures.add(
                        "File ${file.path} has expected state $expectedChangeType" +
                                " but its actual state is $actualChangeType"
                    )
                }
            }
            assert(assertionFailures.isEmpty()) { assertionFailures.joinToString("\n") }
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
