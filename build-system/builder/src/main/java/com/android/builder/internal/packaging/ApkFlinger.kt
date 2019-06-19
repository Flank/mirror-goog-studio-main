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

package com.android.builder.internal.packaging

import com.android.SdkConstants
import com.android.tools.build.apkzlib.zfile.ApkCreator
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode
import com.google.common.base.Function
import com.google.common.base.Preconditions
import com.google.common.base.Predicate
import zipflinger.FileSource
import zipflinger.ZipArchive
import zipflinger.ZipSource
import java.io.File
import java.io.IOException
import java.lang.RuntimeException
import java.nio.file.InvalidPathException
import java.util.zip.Deflater

// TODO support signing - https://issuetracker.google.com/135202380
// TODO ensure that all input zip entries have desired compression -
//  https://issuetracker.google.com/135275558

/** An implementation of [ApkCreator] using the zipflinger library */
class ApkFlinger(creationData: ApkCreatorFactory.CreationData) : ApkCreator {

    /** The zipArchive file.  */
    private val zipArchive: ZipArchive

    /** Predicate defining which files should not be compressed.  */
    private val noCompressPredicate: Predicate<String>

    /** Predicate defining which files should be page aligned.  */
    private val pageAlignPredicate: Predicate<String>

    init {
        when (creationData.nativeLibrariesPackagingMode) {
            NativeLibrariesPackagingMode.COMPRESSED -> {
                noCompressPredicate = creationData.noCompressPredicate
                pageAlignPredicate = Predicate { false }
            }
            NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED -> {
                val baseNoCompressPredicate = creationData.noCompressPredicate
                noCompressPredicate = Predicate { name ->
                    baseNoCompressPredicate.apply(name)
                            || name?.endsWith(SdkConstants.DOT_NATIVE_LIBS) ?: false
                }
                pageAlignPredicate =
                    Predicate { it?.endsWith(SdkConstants.DOT_NATIVE_LIBS) ?: false }
            }
            else -> throw AssertionError()
        }
        zipArchive = ZipArchive(creationData.apkPath)
    }

    /**
     * Copies the content of a Jar/Zip archive into the receiver archive.
     *
     * <p>An optional predicate allows to selectively choose which files to copy over and an
     * optional function allows renaming the files as they are copied.
     *
     * <p>If any entries in zip already exist in this zipArchive, they must be deleted with
     * [deleteFile] before calling this method.
     *
     * <p>After calling this method, any calls at all to [deleteFile] will result in an error.
     *
     * @param zip the zip to copy data from
     * @param transform an optional transform to apply to file names before copying them
     * @param isIgnored an optional filter or {@code null} to mark which out files should not be
     *     added, even through they are on the zip; if {@code transform} is specified, then this
     *     predicate applies after transformation
     * @throws IOException I/O error
     */
    @Throws(IOException::class)
    override fun writeZip(
        zip: File, transform: Function<String, String>?, isIgnored: Predicate<String>?
    ) {
        Preconditions.checkArgument(zip.isFile, "!zip.isFile()")

        val ignorePredicate : Predicate<String> = isIgnored ?: Predicate { false }

        val zipSource = ZipSource(zip)
        val entries = zipSource.entries().values
        for (entry in entries) {
            if (entry.isDirectory || ignorePredicate.apply(entry.name)) {
                continue
            }
            val name = transform?.apply(entry.name) ?: entry.name
            if (name.contains("../")) {
                throw InvalidPathException(name, "Entry name contains invalid characters")
            }
            val zipSourceEntry = zipSource.select(entry.name, name)
            if (!entry.isCompressed) {
                if (pageAlignPredicate.apply(name)) {
                    zipSourceEntry.align(PAGE_ALIGNMENT)
                } else {
                    // by default all uncompressed entries are aligned at 4 byte boundaries.
                    zipSourceEntry.align(DEFAULT_ALIGNMENT)
                }
            }
        }
        zipArchive.add(zipSource)
    }

    /**
     * Writes a new [File] into the archive.
     *
     * <p>If this file entry already exists in this zipArchive, it must be deleted with [deleteFile]
     * before calling this method.
     *
     * <p>After calling this method, any calls at all to [deleteFile] will result in an error.
     *
     * @param inputFile the {@link File} to write.
     * @param apkPath the filepath inside the archive.
     * @throws IOException I/O error
     */
    @Throws(IOException::class)
    override fun writeFile(inputFile: File, apkPath: String) {
        val mayCompress = !noCompressPredicate.apply(apkPath)
        val compressionLevel = if (mayCompress) Deflater.BEST_SPEED else Deflater.NO_COMPRESSION
        val fileSource = FileSource(inputFile, apkPath, compressionLevel)
        if (!mayCompress) {
            if (pageAlignPredicate.apply(apkPath)) {
                fileSource.align(PAGE_ALIGNMENT)
            } else {
                // by default all uncompressed entries are aligned at 4 byte boundaries.
                fileSource.align(DEFAULT_ALIGNMENT)
            }
        }
        zipArchive.add(fileSource)
    }

    /**
     * Deletes the entry with the given apkPath from the [ZipArchive].
     *
     * <p>If this method is called after any writeZip() or writeFile() calls, an error will be
     * thrown.
     *
     * @param apkPath the path to remove
     * @throws IOException failed to remove the entry
     */
    @Throws(IOException::class)
    override fun deleteFile(apkPath: String) {
        zipArchive.delete(apkPath)
    }

    // This is never called. We can delete this method once we migrate away from implementing
    // apkzlib's ApkCreator
    @Throws(IOException::class)
    override fun hasPendingChangesWithWait(): Boolean {
        throw RuntimeException("not implemented")
    }

    @Throws(IOException::class)
    override fun close() {
        zipArchive.close()
    }
}

enum class ApkCreatorType {
    APK_FLINGER,
    APK_Z_FILE_CREATOR
}

private val DEFAULT_ALIGNMENT = 4L
private val PAGE_ALIGNMENT = 4096L
