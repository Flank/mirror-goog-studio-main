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

import com.android.SdkConstants
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

private val isWindows = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;
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
    inline operator fun Path.invoke(block: DirectoryContext.() -> Unit): Path {
        val dir = this.resolveInContext().makeDirectoryReadyToUse()
        DirectoryContext(dir).block()
        return dir
    }

    /** Inflates the receiver path with content of the zip file. */
    infix fun Path.inflatedBy(zipFile: File): Path =
        this.resolveInContext().apply { unzip(zipFile, this.toFile()) }

    /** Inflates the receiver path with content of the zip file. */
    infix fun Path.inflatedBy(zipFile: Path): Path =
        this.resolveInContext() inflatedBy zipFile.toFile()

    /**
     * Uses the receiver path to identify a file and invoke the passed in block for further file
     * operations.
     */
    inline operator fun Path.minus(block: (Path) -> Unit): Path {
        val file = this.resolveInContext().makeFileReadyToUse()
        block(file)
        return file
    }

    /** Uses the receiver path to identify a file and write the content to the file. */
    operator fun Path.minus(content: String): Path {
        val file = this.resolveInContext().makeFileReadyToUse()
        file.toFile().writeText(content)
        return file
    }

    /** Uses the receiver path to identify a file and write the content to the file. */
    operator fun Path.minus(content: ByteArray): Path {
        val file = this.resolveInContext().makeFileReadyToUse()
        file.toFile().writeBytes(content)
        return file
    }

    /** Links receiver path to the given target symbolically. */
    infix fun Path.linkTo(target: Path): Path {
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

    /** Links receiver path to the given target symbolically. */
    infix fun Path.linkTo(target: File): Path = this linkTo target.toPath()

    /** Similar to [Path.invoke] but on a [File]. */
    inline operator fun File.invoke(block: DirectoryContext.() -> Unit): File =
        toPath()(block).toFile()

    /** Similar to [Path.inflatedBy] but on a file. */
    infix fun File.inflatedBy(zipFile: File): File = (this.toPath() inflatedBy zipFile).toFile()

    /** Similar to [Path.inflatedBy] but on a file. */
    infix fun File.inflatedBy(zipFile: Path): File = (this.toPath() inflatedBy zipFile).toFile()

    /** Similar to [Path.minus] but on a [File]. */
    inline operator fun File.minus(block: (Path) -> Unit): File = (toPath() - block).toFile()

    /** Similar to [Path.minus] but on a [File]. */
    operator fun File.minus(content: String): File = (toPath() - content).toFile()

    /** Similar to [Path.minus] but on a [File]. */
    operator fun File.minus(content: ByteArray): File = (toPath() - content).toFile()

    /** Similar to [Path.linkTo] but on a file */
    infix fun File.linkTo(target: Path) = toPath() linkTo target

    /** Similar to [Path.linkTo] but on a file */
    infix fun File.linkTo(target: File) = toPath() linkTo target

    /** Similar to [Path.invoke] but on a [String] treated as a file path. */
    inline operator fun String.invoke(block: DirectoryContext.() -> Unit) = Paths.get(this)(block)

    /** Similar to [Path.inflatedBy] but on a string. */
    infix fun String.inflatedBy(zipFile: File): Path = Paths.get(this) inflatedBy zipFile

    /** Similar to [Path.inflatedBy] but on a string. */
    infix fun String.inflatedBy(zipFile: Path): Path = Paths.get(this) inflatedBy zipFile

    /** Similar to [Path.minus] but on a [String] treated as a file path. */
    inline operator fun String.minus(block: (Path) -> Unit) = Paths.get(this) - block

    /** Similar to [Path.minus] but on a [String] treated as a file path. */
    operator fun String.minus(content: String) = Paths.get(this) - content

    /** Similar to [Path.minus] but on a [String] treated as a file path. */
    operator fun String.minus(content: ByteArray) = Paths.get(this) - content

    /** Similar to [Path.linkTo] but on a string */
    infix fun String.linkTo(target: Path) = Paths.get(this) linkTo target

    /** Similar to [Path.linkTo] but on a string */
    infix fun String.linkTo(target: File) = Paths.get(this) linkTo target

    /** Creates a [Path] relative to the current directory context. */
    fun path(s: String): Path = Paths.get(s).resolveInContext()

    /** Creates a [File] relative to the current directory context. */
    fun file(s: String): File = path(s).toFile()

    /** Resolves the path relative to the current directory context. */
    fun Path.resolveInContext() = directory / this
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
 * Uses the receiver path to build a directory context and invoke the passed in block for
 * further file building.
 */
inline operator fun Path.invoke(block: DirectoryContext.() -> Unit): Path {
    val dir = this.ensureIsAbsolute().makeDirectoryReadyToUse()
    DirectoryContext(dir).block()
    return dir
}

/** Similar to [Path.invoke] but on a [File]. */
inline operator fun File.invoke(block: DirectoryContext.() -> Unit): File = toPath()(block).toFile()

/** Similar to [Path.invoke] but on a [String] treated as a path. */
inline operator fun String.invoke(block: DirectoryContext.() -> Unit) = Paths.get(this)(block)

@Throws(IOException::class)
infix fun Path.inflatedBy(zipFile: File): Path {
    val dir = this.ensureIsAbsolute().makeDirectoryReadyToUse()
    unzip(zipFile, dir.toFile())
    return dir
}

@Throws(IOException::class)
infix fun Path.inflatedBy(zipFilePath: Path) = this inflatedBy zipFilePath.toFile()

@Throws(IOException::class)
infix fun File.inflatedBy(zipFilePath: Path) = this.toPath() inflatedBy zipFilePath.toFile()

@Throws(IOException::class)
infix fun File.inflatedBy(zipFile: File) = this.toPath() inflatedBy zipFile

/**
 * Converts a [String] to an appender function that appends to a file identified by the given [Path].
 */
operator fun String.unaryPlus(): (Path) -> Unit = {
    it.toFile().appendText(this)
}

/** Concatenates this path with the given string. */
operator fun Path.div(s: String): Path = this.resolve(s)
/** Concatenates this path with the given path. */
operator fun Path.div(p: Path): Path = this.resolve(p)
/** Concatenates this path with the given file. */
operator fun Path.div(f: File): Path = this.resolve(f.toPath())
/** Concatenates this path with the given string. */
operator fun File.div(s: String): File = (toPath() / s).toFile()
/** Concatenates this path with the given path. */
operator fun File.div(p: Path): File = (toPath() / p).toFile()
/** Concatenates this path with the given file. */
operator fun File.div(f: File): File = (toPath() / f.toPath()).toFile()

/** Unzips the content of the given file into the given directory. */
@Throws(IOException::class)
fun unzip(zipFile: File, dir: File) {
    ZipFile(zipFile).use { zip ->
        zip.entries().asSequence()
            .filter { !it.name.endsWith("/") } // filter out directory entries
            .forEach { entry ->
                zip.getInputStream(entry)
                    .use { input ->
                        (dir / entry.name).apply { parentFile.mkdirs() }
                            .outputStream()
                            .use { output ->
                                input.copyTo(output)
                            }
                    }
            }
    }
}
