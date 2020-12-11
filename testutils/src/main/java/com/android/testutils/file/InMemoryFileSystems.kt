/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("InMemoryFileSystems")
package com.android.testutils.file

import com.android.testutils.OsType
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.io.File
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import kotlin.streams.toList

/**
 * Creates an in-memory file system with a configuration appropriate for the current platform.
 */
fun createFileSystem(): FileSystem {
    var config = Configuration.forCurrentPlatform()
    config = config.toBuilder().apply {
        if (OsType.getHostOs() == OsType.WINDOWS) {
            setRoots("C:\\")
            setWorkingDirectory("C:\\")
        } else {
            setRoots("/")
            setWorkingDirectory("/")
        }
        setAttributeViews("posix")
    }.build()
    return Jimfs.newFileSystem(config)
}

/**
 * Creates an in-memory file system with a configuration appropriate for the current platform and
 * a folder with the given name on that file system.
 */
fun createFileSystemAndFolder(folderName: String): Path {
    val fileSystem = createFileSystem()
    // On Windows the folder is created on the last drive.
    return Files.createDirectory(fileSystem.someRoot.resolve(folderName))
}

/**
 * Some root of the file system. On Windows it is the root directory of the last drive, on Linux and
 * Mac it is "/".
 */
val FileSystem.someRoot: Path
  get() = rootDirectories.last()

fun canWrite(path: Path): Boolean {
    return try {
        !Sets.intersection(
            Files.getPosixFilePermissions(path),
            ImmutableSet.of(
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OWNER_WRITE
            )
        )
            .isEmpty()
    } catch (e: IOException) {
        false
    }
}

fun getPlatformSpecificPath(path: String): String {
    return if (OsType.getHostOs() == OsType.WINDOWS) {
        (if (path.startsWith('/') || path.startsWith('\\')) "C:" else "") +
                path.replace('/', File.separatorChar)
    } else path
}

/**
 * Records a new absolute file path.
 * Parent folders are automatically created.
 */
fun recordExistingFile(path: Path, contents: String?) =
        recordExistingFile(path, 0, contents?.toByteArray())

/**
 * Records a new absolute file path.
 * Parent folders are automatically created.
 */
fun recordExistingFile(path: Path, lastModified: Long = 0, contents: ByteArray? = null) {
    try {
        Files.createDirectories(path.parent)
        Files.write(path, contents ?: ByteArray(0))
        Files.setLastModifiedTime(path, FileTime.fromMillis(lastModified))
    } catch (e: IOException) {
        assert(false) { e.message!! }
    }
}

/**
 * Returns the list of paths added using [.recordExistingFile]
 * and eventually updated by [.delete] operations.
 *
 * The returned list is sorted by alphabetic absolute path string.
 */
fun getExistingFiles(fileSystem: FileSystem): Array<String> {
    return fileSystem.rootDirectories
        .flatMap { Files.walk(it).use { it.toList() } }
        .filter { Files.isRegularFile(it) }
        .map { it.toString() }
        .sorted()
        .toTypedArray()
}

/**
 * Returns the list of folder paths added using {@link #recordExistingFolder(String)}
 * and eventually updated {@link #delete(File)} or {@link #mkdirs(File)} operations.
 * <p>
 * The returned list is sorted by alphabetic absolute path string.
 */
fun getExistingFolders(fileSystem: FileSystem): Array<String> {
    return fileSystem.rootDirectories
        .flatMap { Files.walk(it).use { it.toList() } }
        .filter { Files.isDirectory(it) }
        .map { it.toString() }
        .sorted()
        .toTypedArray()
}
