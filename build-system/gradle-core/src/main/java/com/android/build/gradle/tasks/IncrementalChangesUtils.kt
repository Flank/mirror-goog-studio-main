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
@file:JvmName("IncrementalChangesUtils")
@file:Suppress("UnstableApiUsage") // Uses incubating gradle APIs.

package com.android.build.gradle.tasks

import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileType
import org.gradle.api.provider.Provider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.InputChanges
import java.util.Collections

/**
 * Convert Gradle incremental changes to a serializable form for the worker API.
 *
 * This method ignores directory changes.
 */
fun InputChanges.getChangesInSerializableForm(input: Provider<out FileSystemLocation>) =
    convert(getFileChanges(input))

/**
 * Convert Gradle incremental changes to a serializable form for the worker API.
 *
 * This method ignores directory changes.
 */
fun InputChanges.getChangesInSerializableForm(input: FileCollection) =
    convert(getFileChanges(input))

private fun convert(changes: Iterable<FileChange>): Collection<SerializableChange> {
    return Collections.unmodifiableCollection(ArrayList<SerializableChange>().also { collection ->
        for (change in changes) {
            if (change.fileType == FileType.FILE) {
                collection.add(toSerializable(change))
            }
        }
    })
}

private fun convert(changeType: ChangeType): FileStatus = when (changeType) {
    ChangeType.ADDED -> FileStatus.NEW
    ChangeType.MODIFIED -> FileStatus.CHANGED
    ChangeType.REMOVED -> FileStatus.REMOVED
}

private fun toSerializable(change: FileChange): SerializableChange {
    return SerializableChange(change.file,
        convert(change.changeType), change.normalizedPath)
}