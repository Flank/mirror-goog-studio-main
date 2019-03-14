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

package com.android.build.gradle.integration.common

import com.android.SdkConstants
import com.android.utils.PathUtils
import com.google.common.base.Preconditions
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

private val isWindows = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS

/**
 * A directory context passes an implicit directory around for file creation.
 *
 * For an example usage see [FileBuilderTest].
 */
class DirectoryContext @PublishedApi internal constructor(private val directory: Path) {

    /**
     * Uses the receiver path to build a new directory context and invoke the passed in block for
     * further file building.
     */
    inline fun dir(p: Path, block: DirectoryContext.() -> Unit = {}): Path {
        val dir = p.resolveInContext().makeDirectoryReadyToUse()
        DirectoryContext(dir).block()
        return dir
    }

    /** Similar to [dir]. */
    inline fun dir(f: File, block: DirectoryContext.() -> Unit = {}): File =
        dir(f.toPath(), block).toFile()

    /** Similar to [dir]. */
    inline fun dir(s: String, block: DirectoryContext.() -> Unit = {}): Path =
        dir(Paths.get(s), block)

    /**
     * Uses the receiver path to identify a file and invoke the passed in block for further file
     * operations.
     */
    inline fun file(p: Path, block: File.() -> Unit = {}): Path {
        val file = p.resolveInContext().makeFileReadyToUse()
        file.toFile().block()
        return file
    }

    /** Similar to [file]. */
    inline fun file(f: File, block: File.() -> Unit = {}): File = file(f.toPath(), block).toFile()

    /** Similar to [file]. */
    inline fun file(s: String, block: File.() -> Unit = {}): Path = file(Paths.get(s), block)

    /** Inflates the receiver path with content of the zip file. */
    @Throws(IOException::class)
    fun Path.inflatedBy(zipFile: File): Path =
        this.resolveInContext().apply { unzip(zipFile, this.toFile()) }

    /** Similar to [Path.inflatedBy]. */
    @Throws(IOException::class)
    fun Path.inflatedBy(zipFile: Path): Path = this.inflatedBy(zipFile.toFile())

    /** Similar to [Path.inflatedBy]. */
    @Throws(IOException::class)
    fun File.inflatedBy(zipFile: File): File = (this.toPath().inflatedBy(zipFile)).toFile()

    /** Similar to [Path.inflatedBy]. */
    @Throws(IOException::class)
    fun File.inflatedBy(zipFile: Path): File = this.inflatedBy(zipFile.toFile())

    /** Copies to receiver path with content of the src file. */
    @Throws(IOException::class)
    fun Path.copyFrom(src: File): Path {
        return this.resolveInContext().apply { copy(src, this.toFile()) }
    }

    /** Similar to [Path.copyFrom]. */
    @Throws(IOException::class)
    fun Path.copyFrom(src: Path): Path = this.copyFrom(src.toFile())

    /** Similar to [Path.copyFrom]. */
    @Throws(IOException::class)
    fun File.copyFrom(src: File): File = (this.toPath().copyFrom(src)).toFile()

    /** Similar to [Path.copyFrom]. */
    @Throws(IOException::class)
    fun File.copyFrom(src: Path): File = this.copyFrom(src.toFile())

    /** Links receiver path to the given target symbolically. */
    fun Path.linkTo(target: Path): Path {
        val file = this.resolveInContext().makeFileReadyToUse()
        val actualTarget =
            if (isWindows) {
                // Due to a bug of recent versions of Windows, one can only link to a relative
                // target. See https://github.com/moby/moby/issues/38220
                file.parent.relativize(target)
            } else {
                target
            }
        Files.createSymbolicLink(file, actualTarget)
        return file
    }

    /** Similar to [Path.linkTo]. */
    fun Path.linkTo(target: File): Path = this.linkTo(target.toPath())

    /** Similar to [Path.copyFrom]. */
    @Throws(IOException::class)
    fun File.linkTo(src: File): File = (this.toPath().linkTo(src)).toFile()

    /** Similar to [Path.copyFrom]. */
    @Throws(IOException::class)
    fun File.linkTo(src: Path): File = this.linkTo(src.toFile())

    /** Resolves the path relative to the current directory context. */
    fun Path.resolveInContext(): Path = directory.resolve(this)
}

@PublishedApi
internal fun Path.makeDirectoryReadyToUse(): Path {
    if (Files.exists(this)) {
        if (!Files.isDirectory(this)) {
            throw IllegalArgumentException("${this} is a file.")
        }
    } else {
        toFile().mkdirs()
    }
    return this
}

@PublishedApi
internal fun Path.makeFileReadyToUse(): Path {
    if (Files.isDirectory(this)) {
        throw IllegalArgumentException("${this} is a directory.")
    }
    this.parent.toFile().mkdirs()
    return this
}

@PublishedApi
internal fun Path.ensureIsAbsolute(): Path {
    if (!isAbsolute) {
        throw IllegalArgumentException("Must start FileBuilder from an absolute path. $this is relative.")
    }
    return this
}

/**
 * Uses the receiver path to build a new directory context and invoke the passed in block for
 * further file building.
 */
inline fun dir(p: Path, block: DirectoryContext.() -> Unit = {}): Path {
    val dir = p.ensureIsAbsolute().makeDirectoryReadyToUse()
    DirectoryContext(dir).block()
    return dir
}

/** Similar to [dir]. */
inline fun dir(f: File, block: DirectoryContext.() -> Unit = {}): File =
    dir(f.toPath(), block).toFile()

/** Concatenates this path with the given file. */
fun Path.resolve(f: File): Path = this.resolve(f.toPath())

/** Concatenates this path with the given string. */
fun File.resolve(s: String): File = (toPath().resolve(s)).toFile()

/** Concatenates this path with the given path. */
fun File.resolve(p: Path): File = (toPath().resolve(p)).toFile()

/** Concatenates this path with the given file. */
fun File.resolve(f: File): File = (toPath().resolve(f.toPath())).toFile()

fun File.deleteRecursivelyIfExists(): Unit = PathUtils.deleteRecursivelyIfExists(this.toPath())
fun Path.deleteRecursivelyIfExists(): Unit = PathUtils.deleteRecursivelyIfExists(this)
fun File.deleteRecursivelyOnExit(): File =
    apply { PathUtils.addRemovePathHook(this.toPath()) }

fun Path.deleteRecursivelyOnExit(): Path =
    apply { PathUtils.addRemovePathHook(this) }

/** Unzips the content of the given file into the given directory. */
@Throws(IOException::class)
fun unzip(zipFile: File, dir: File) {
    ZipFile(zipFile).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory } // filter out directory entries
            .forEach { entry ->
                zip.getInputStream(entry)
                    .use { input ->
                        (dir.resolve(entry.name)).apply { parentFile.mkdirs() }
                            .outputStream()
                            .use { output ->
                                input.copyTo(output)
                            }
                    }
            }
    }
}

@Throws(IOException::class)
fun copy(src: File, dest: File) {
    Preconditions.checkArgument(
        !dest.exists() || dest.isDirectory == src.isDirectory,
        if (dest.isDirectory) {
            "Cannot copy $src to ${dest} since the former is a file yet the latter a directory."
        } else {
            "Cannot copy $src to ${dest} since the former is a directory yet the latter a file."
        }
    )
    src.copyRecursively(dest)
}
