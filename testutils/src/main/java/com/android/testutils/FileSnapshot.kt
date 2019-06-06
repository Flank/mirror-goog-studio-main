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

package com.android.testutils

import java.io.File

/**
 * A snapshot of the file hierarchy and contents at some filesystem location.
 */
class FileSnapshot private constructor(
    val directorySet: Set<File>,
    val regularFileContentsMap: Map<File, ByteArray>
) {

    companion object {

        /**
         * Takes a snapshot of the given regular file or directory. If a base directory is provided,
         * the snapshot stores the file paths relative to it; otherwise, it stores absolute file
         * paths.
         */
        fun snapshot(fileToSnapshot: File, baseDir: File? = null): FileSnapshot {
            val directorySet = mutableSetOf<File>()
            val regularFileContentsMap = mutableMapOf<File, ByteArray>()

            doSnapshot(fileToSnapshot, baseDir, directorySet, regularFileContentsMap)

            return FileSnapshot(directorySet.toSet(), regularFileContentsMap.toMap())
        }

        private fun doSnapshot(
            fileToSnapshot: File,
            baseDir: File? = null,
            directorySet: MutableSet<File>,
            regularFileContentsMap: MutableMap<File, ByteArray>
        ) {
            val normalizedFile = if (baseDir != null) {
                fileToSnapshot.relativeTo(baseDir)
            } else {
                fileToSnapshot
            }

            if (fileToSnapshot.isDirectory) {
                directorySet.add(normalizedFile)
                for (fileInDir in fileToSnapshot.listFiles()!!) {
                    doSnapshot(fileInDir, baseDir, directorySet, regularFileContentsMap)
                }
            } else {
                regularFileContentsMap[normalizedFile] = fileToSnapshot.readBytes()
            }
        }
    }
}