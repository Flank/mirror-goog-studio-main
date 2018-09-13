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

@file:JvmName("IncrementalFileMergerTaskUtils")

package com.android.build.gradle.internal.tasks

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import com.android.builder.files.FileCacheByPath
import com.android.builder.files.IncrementalRelativeFileSets
import com.android.builder.files.RelativeFile
import com.android.builder.files.RelativeFiles
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.builder.merge.LazyIncrementalFileMergerInput
import com.android.builder.merge.LazyIncrementalFileMergerInputs
import com.android.ide.common.resources.FileStatus
import com.android.tools.build.apkzlib.utils.CachedSupplier
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.HashSet


// TODO update this method in subsequent CL
/**
 * Creates an [IncrementalFileMergerInput] from a [JarInput]. All files in the jar
 * will be reported in the incremental input. This method assumes the input contains
 * incremental information.
 *
 * @param jarInput the jar input
 * @param zipCache the zip cache; the cache will not be modified
 * @param cacheUpdate will receive actions to update the cache for the next iteration
 * @param contentMap if not `null`, receives a mapping from all generated inputs to
 * [QualifiedContent] they came from
 * @return the input
 */
fun toIncrementalInput(
    jarInput: JarInput,
    zipCache: FileCacheByPath,
    cacheUpdate: MutableList<Runnable>,
    contentMap: MutableMap<IncrementalFileMergerInput, QualifiedContent>?
): IncrementalFileMergerInput {
    val jarFile = jarInput.file
    if (jarFile.isFile) {
        cacheUpdate.add(IOExceptionRunnable.asRunnable { zipCache.add(jarFile) })
    } else {
        cacheUpdate.add(IOExceptionRunnable.asRunnable { zipCache.remove(jarFile) })
    }

    val input = LazyIncrementalFileMergerInput(
        jarFile.absolutePath,
        CachedSupplier { computeUpdates(jarInput, zipCache) },
        CachedSupplier { computeFiles(jarInput) })

    contentMap?.let { it[input] = jarInput }

    return input
}

// TODO update this method in subsequent CL
/**
 * Creates an [IncrementalFileMergerInput] from a [DirectoryInput]. All files in the
 * directory will be reported in the incremental input. This method assumes the input contains
 * incremental information.
 *
 * @param directoryInput the directory input
 * @param contentMap if not `null`, receives a mapping from all generated inputs to
 * [QualifiedContent] they came from
 * @return the input
 */
fun toIncrementalInput(
    directoryInput: DirectoryInput,
    contentMap: MutableMap<IncrementalFileMergerInput, QualifiedContent>?
): IncrementalFileMergerInput {
    val input = LazyIncrementalFileMergerInput(
        directoryInput.file.absolutePath,
        CachedSupplier { computeUpdates(directoryInput) },
        CachedSupplier { computeFiles(directoryInput) })

    contentMap?.let { it[input] = directoryInput }

    return input
}

/**
 * Creates an [IncrementalFileMergerInput] from a [File]. This method assumes the input does not
 * contain incremental information. All files will be reported as new.
 *
 * @param file the file input
 * @param zipCache the zip cache; the cache will not be modified
 * @param cacheUpdates will receive actions to update the cache for the next iteration, if
 *        file.isFile is true, in which case it's assumed to be a jar file.
 * @return the input or `null` if the file does not exist
 */
fun toNonIncrementalInput(
    file: File,
    fileCache: FileCacheByPath,
    cacheUpdates: MutableList<Runnable>
): IncrementalFileMergerInput? {
    if (!file.isFile && !file.isDirectory) {
        return null
    }

    if (file.isFile) {
        cacheUpdates.add(IOExceptionRunnable.asRunnable {  fileCache.add(file) })
    }

    return LazyIncrementalFileMergerInputs.fromNew(file.absolutePath, ImmutableSet.of(file))
}

// TODO update this method in subsequent CL
/**
 * Computes all updates in a [JarInput].
 *
 * @param jarInput the jar input
 * @param zipCache the cache of zip files; the cache will not be modified
 * @return a mapping from all files that have changed to the type of change
 */
private fun computeUpdates(
    jarInput: JarInput,
    zipCache: FileCacheByPath
): ImmutableMap<RelativeFile, FileStatus> {
    try {
        when (jarInput.status) {
            Status.ADDED -> return IncrementalRelativeFileSets.fromZip(
                jarInput.file,
                FileStatus.NEW
            )
            Status.REMOVED -> {
                val cached = zipCache.get(jarInput.file) ?: throw RuntimeException(
                    "File '" + jarInput.file + "' was "
                            + "deleted, but previous version not found in cache"
                )

                return IncrementalRelativeFileSets.fromZip(cached, FileStatus.REMOVED)
            }
            Status.CHANGED -> return IncrementalRelativeFileSets.fromZip(
                jarInput.file,
                zipCache,
                HashSet()
            )
            Status.NOTCHANGED -> return ImmutableMap.of()
            else -> throw AssertionError()
        }
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }

}

// TODO update this method in subsequent CL
/**
 * Computes a set with all files in a [JarInput].
 *
 * @param jarInput the jar input
 * @return all files in the input
 */
private fun computeFiles(jarInput: JarInput): ImmutableSet<RelativeFile> {
    val jar = jarInput.file
    assert(jar.isFile)

    try {
        return RelativeFiles.fromZip(jar)
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }

}

// TODO update this method in subsequent CL
/**
 * Computes all updates in a [DirectoryInput].
 *
 * @param directoryInput the directory input
 * @return a mapping from all files that have changed to the type of change
 */
private fun computeUpdates(
    directoryInput: DirectoryInput
): ImmutableMap<RelativeFile, FileStatus> {
    val builder = ImmutableMap.builder<RelativeFile, FileStatus>()

    val changedFiles = directoryInput.changedFiles
    for ((key, value) in changedFiles) {
        val rf = RelativeFile(directoryInput.file, key)
        val status = mapStatus(value)
        if (status != null && !File(rf.base, rf.relativePath).isDirectory) {
            builder.put(rf, status)
        }
    }

    return builder.build()
}

// TODO update this method in subsequent CL
/**
 * Computes a set with all files in a [DirectoryInput].
 *
 * @param directoryInput the directory input
 * @return all files in the input
 */
private fun computeFiles(directoryInput: DirectoryInput): ImmutableSet<RelativeFile> {
    val dir = directoryInput.file
    assert(dir.isDirectory)
    return RelativeFiles.fromDirectory(dir)
}

/**
 * Creates a list of [IncrementalFileMergerInput] from a map of [File]s to [Scope]s.
 *
 * @param inputMap map of files to their corresponding scopes
 * @param changedInputs map of files to file status, passed from the incremental task, or null if
 * the task is not incremental
 * @param fileCache the zip cache; the cache will not be modified
 * @param cacheUpdates receives updates to the cache
 * @param full is this a full build? If not, then it is an incremental build; in full builds
 * the output is not cleaned, it is the responsibility of the caller to ensure the output
 * is properly set up; `full` cannot be `false` if the transform invocation is not
 * stating that the invocation is an incremental one
 * @param contentType the ContentType of files being merged
 * @param contentMap if not `null`, receives a mapping from all generated inputs to
 * [QualifiedContent] they came from
 */
fun toInputs(
    inputMap: MutableMap<File, in Scope>,
    changedInputs: Map<File, FileStatus>?,
    fileCache: FileCacheByPath,
    cacheUpdates: MutableList<Runnable>,
    full: Boolean,
    contentType: ContentType,
    contentMap: MutableMap<IncrementalFileMergerInput, QualifiedContent>?
): ImmutableList<IncrementalFileMergerInput> {
    if (!full) {
        changedInputs ?: throw RuntimeException(
            "changedInputs must be specified for incremental merging."
        )
        // TODO remove removed inputs?
    }

    if (full) {
        cacheUpdates.add(IOExceptionRunnable.asRunnable { fileCache.clear() })
    }

    val builder = ImmutableList.builder<IncrementalFileMergerInput>()
    for ((input, scope) in inputMap.entries) {
        val qualifiedContent =
            object: QualifiedContent {
                override fun getName() = "file-merger-qualified-content"
                override fun getFile() = input
                override fun getContentTypes() = mutableSetOf(contentType)
                override fun getScopes() = mutableSetOf(scope)
            }
        val fileMergerInput: IncrementalFileMergerInput? = if (full) {
            toNonIncrementalInput(input, fileCache, cacheUpdates)
        } else {
            // TODO incremental case
            null
        }

        fileMergerInput?.let {
            builder.add(it)
            // Add mapping of fileMergerInput to qualifiedContent if contentMap != null
            contentMap?.let { contentMap -> contentMap[it] = qualifiedContent}
        }
    }

    return builder.build()
}

/**
 * Maps a [Status] to a [FileStatus].
 *
 * @param status the status
 * @return the [FileStatus] or `null` if `status` is [Status.NOTCHANGED]
 */
private fun mapStatus(status: Status): FileStatus? {
    return when (status) {
        Status.ADDED -> FileStatus.NEW
        Status.CHANGED -> FileStatus.CHANGED
        Status.NOTCHANGED -> null
        Status.REMOVED -> FileStatus.REMOVED
    }
}
