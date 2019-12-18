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
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/** Utility to write tests for incremental tasks. */
class IncrementalTestHelper(
    private val executor: GradleTaskExecutor,
    private val cleanTask: String = "clean",
    private val buildTask: String = "assembleDebug",
    private val filesToTrackChanges: Set<File>
) {

    // Used to ensure the steps in the API are followed
    private var stage: Stage = Stage.INITIAL

    // Record the timestamps and contents of files in the first (full) build
    private lateinit var fileTimestamps: Map<File, FileTime>
    private lateinit var fileContents: Map<File, ByteArray>

    // Record files with changed timestamps or contents in the second (incremental) build
    private lateinit var filesWithChangedTimestamps: Set<File>
    private lateinit var filesWithChangedContents: Set<File>

    /** Runs a full build. */
    fun runFullBuild(): IncrementalTestHelper {
        check(stage < Stage.RAN_FULL_BUILD) { "runFullBuild was already called" }
        check(stage == Stage.INITIAL) { "Expected ${Stage.INITIAL.name} but found: ${stage.name}" }

        executor.run(cleanTask, buildTask)

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

        stage = Stage.RAN_FULL_BUILD
        return this
    }

    /** Applies a change. */
    fun applyChange(change: () -> Unit): IncrementalTestHelper {
        check(stage < Stage.APPLIED_CHANGE) { "applyChange was already called" }
        check(stage == Stage.RAN_FULL_BUILD) { "runFullBuild must be called first" }

        change()

        stage = Stage.APPLIED_CHANGE
        return this
    }

    /** Runs an incremental build. */
    fun runIncrementalBuild(): IncrementalTestHelper {
        check(stage < Stage.RAN_INCREMENTAL_BUILD) { "runIncrementalBuild was already called" }
        check(stage == Stage.APPLIED_CHANGE) { "applyChange must be called first" }

        executor.run(buildTask)

        // Record changed files
        filesWithChangedTimestamps = fileTimestamps.filter { (file, previousTimestamp) ->
            check(file.isFile) { "File `${file.path}` does not exist or is a directory." }
            Files.getLastModifiedTime(file.toPath()) != previousTimestamp
        }.keys
        filesWithChangedContents = fileContents.filter { (file, previousContents) ->
            check(file.isFile) { "File `${file.path}` does not exist or is a directory." }
            !file.readBytes().contentEquals(previousContents)
        }.keys

        stage = Stage.RAN_INCREMENTAL_BUILD
        return this
    }

    /** Asserts file changes. */
    fun assertFileChanges(fileChanges: Map<File, ChangeType>): IncrementalTestHelper {
        return assertFileChanges(
            fileChanges.filterValues { it == ChangeType.CHANGED }.keys,
            fileChanges.filterValues { it == ChangeType.CHANGED_TIMESTAMPS_BUT_NOT_CONTENTS }.keys,
            fileChanges.filterValues { it == ChangeType.UNCHANGED }.keys
        )
    }

    /** Asserts file changes. */
    fun assertFileChanges(
        filesWithChangedTimestampsAndContents: Set<File>,
        filesWithChangedTimestampsButNotContents: Set<File>,
        filesWithUnchangedTimestampsAndContents: Set<File>
    ): IncrementalTestHelper {
        check(stage < Stage.ASSERTED_FILE_CHANGES) { "assertFileChanges was already called" }
        check(stage == Stage.RAN_INCREMENTAL_BUILD) { "runIncrementalBuild must be called first" }

        (filesWithChangedTimestampsAndContents
                + filesWithChangedTimestampsButNotContents
                + filesWithUnchangedTimestampsAndContents).subtract(
            filesToTrackChanges
        ).let {
            check(it.isEmpty()) {
                "The following files are missing from the set of files to track:\n" +
                        it.joinToString("\n")
            }
        }

        val actualFilesWithChangedTimestampsAndContents =
            filesWithChangedTimestamps.intersect(filesWithChangedContents)
        val actualFilesWithChangedTimestampsButNotContents =
            filesWithChangedTimestamps.subtract(filesWithChangedContents)
        val actualFilesWithUnchangedTimestampsAndContents =
            filesToTrackChanges.subtract(filesWithChangedTimestamps.plus(filesWithChangedContents))

        val actualFilesWithChangedContentsButNotTimestamps =
            filesWithChangedContents.subtract(filesWithChangedTimestamps)
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
                            " but either their timestamps or contents have not changed:\n" +
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
                            " but either their timestamps or contents have changed::\n" +
                            it.joinToString("\n")
                }
            }

        stage = Stage.ASSERTED_FILE_CHANGES
        return this
    }
}

/** Indicates the steps in the API of [IncrementalTestHelper]. */
private enum class Stage {
    INITIAL,
    RAN_FULL_BUILD,
    APPLIED_CHANGE,
    RAN_INCREMENTAL_BUILD,
    ASSERTED_FILE_CHANGES
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