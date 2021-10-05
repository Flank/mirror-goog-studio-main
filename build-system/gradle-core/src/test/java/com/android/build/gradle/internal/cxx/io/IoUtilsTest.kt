/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.io

import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_CONTENT
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_DESTINATION_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_LENGTH
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_SOURCE_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_CONTENT
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_CANONICAL_PATH
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_LEXICAL_PATH
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_BY_FILE_OBJECT_IDENTITY
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_SOURCE_AND_DESTINATION_DID_NOT_EXIST
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Path

class IoUtilsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `two files with same object identity are the same`() {
        val file = File("my-file.txt")
        assertThat(compareFileContents(file, file)).isEqualTo(SAME_PATH_BY_FILE_OBJECT_IDENTITY)
    }

    @Test
    fun `two files with same path are the same`() {
        val file1 = File("my-file.txt")
        val file2 = File("my-file.txt")
        assertThat(compareFileContents(file1, file2)).isEqualTo(SAME_PATH_ACCORDING_TO_LEXICAL_PATH)
    }

    @Test
    fun `two files that both don't exist are the same`() {
        val file1 = File("my-file-1.txt")
        file1.delete()
        val file2 = File("my-file-2.txt")
        file2.delete()
        assertThat(compareFileContents(file1, file2)).isEqualTo(
            SAME_SOURCE_AND_DESTINATION_DID_NOT_EXIST
        )
    }

    @Test
    fun `when first file exists and second doesn't they are not the same`() {
        val file1 = temporaryFolder.newFile("my-file-1.txt")
        file1.writeText("exists")
        val file2 = temporaryFolder.newFile("my-file-2.txt")
        file2.delete()
        assertThat(compareFileContents(file1, file2)).isEqualTo(NOT_SAME_DESTINATION_DID_NOT_EXIST)
    }

    @Test
    fun `when first file doesn't exist and second does they are not the same`() {
        val file1 = temporaryFolder.newFile("my-file-1.txt")
        file1.delete()
        val file2 = temporaryFolder.newFile("my-file-2.txt")
        file2.writeText("exists")
        assertThat(compareFileContents(file1, file2)).isEqualTo(NOT_SAME_SOURCE_DID_NOT_EXIST)
    }

    @Test
    fun `when files are different length they are not the same`() {
        val file1 = temporaryFolder.newFile("my-file-1.txt")
        file1.writeText("small file")
        val file2 = temporaryFolder.newFile("my-file-2.txt")
        file2.writeText("larger file")
        assertThat(compareFileContents(file1, file2)).isEqualTo(NOT_SAME_LENGTH)
    }

    @Test
    fun `when files have equivalent paths according to canonical path they are the same`() {
        val folder = temporaryFolder.newFolder()
        folder.resolve("1").mkdirs()
        folder.resolve("2").mkdirs()
        val file1 = folder.resolve("1/../my-file.txt")
        file1.canonicalFile.writeText("same content")
        val file2 = folder.resolve("2/../my-file.txt")
        assertThat(compareFileContents(file1, file2)).isEqualTo(
            SAME_PATH_ACCORDING_TO_CANONICAL_PATH
        )
    }

    @Test
    fun `one file hard linked to another is the same without comparing contents`() {
        val source = temporaryFolder.newFile("my-file-source-1.txt")
        source.writeText("content-1")
        val destination = temporaryFolder.newFile("my-file-source-2.txt")
        destination.writeText("content-2")
        synchronizeFile(source, destination)
        assertThat(compareFileContents(source, destination)).isEqualTo(
            SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER
        )
    }

    @Test
    fun `two files with the same content are the same`() {
        val source = temporaryFolder.newFile("my-file-source-1.txt")
        source.writeText("content-1")
        val destination = temporaryFolder.newFile("my-file-source-2.txt")
        source.copyTo(destination, overwrite = true)
        synchronizeFile(source, destination)
        assertThat(compareFileContents(source, destination)).isEqualTo(
            SAME_CONTENT
        )
    }

    @Test
    fun `synchronizeFile falls back to copy when createLink throws IOException`() {
        val source = temporaryFolder.newFile("my-file-source-1.txt")
        source.writeText("content-1")
        val destination = temporaryFolder.newFile("my-file-source-2.txt")
        destination.writeText("content-2")
        synchronizeFile(source, destination) { _ : Path, _ : Path ->
            // Simulate an IOException
            throw IOException()
        }
        assertThat(compareFileContents(source, destination)).isEqualTo(
            SAME_CONTENT
        )
    }

    @Test
    fun `synchronizeFile missing source file will delete destination`() {
        val source = temporaryFolder.newFile("my-file-source-1.txt")
        source.delete()
        val destination = temporaryFolder.newFile("my-file-source-2.txt")
        destination.writeText("content-2")
        assertThat(destination.isFile).isTrue()
        synchronizeFile(source, destination)
        assertThat(destination.isFile).isFalse()
    }

    @Test
    fun `same files larger than buffer size are same`() {
        val source = temporaryFolder.newFile("my-file-source-1.txt")
        source.writeText("content")
        val destination = temporaryFolder.newFile("my-file-source-2.txt")
        destination.writeText("content")
        assertThat(
            compareFileContents(
                source,
                destination,
                compareBufferSize = 1 // Make the buffer smaller than content
            )).isEqualTo(SAME_CONTENT)
    }

    @Test
    fun `different files larger than buffer size are different`() {
        val source = temporaryFolder.newFile("my-file-source-1.txt")
        source.writeText("content-1")
        val destination = temporaryFolder.newFile("my-file-source-2.txt")
        destination.writeText("content-2")
        assertThat(
            compareFileContents(
                source,
                destination,
                compareBufferSize = 1 // Make the buffer smaller than content
            )).isEqualTo(NOT_SAME_CONTENT)
    }

    @Test
    fun `changing source file changes linked file`() {
        val file1 = temporaryFolder.newFile("my-file-1.txt")
        val file2 = temporaryFolder.newFile("my-file-2.txt")
        file1.writeText("content-A")
        synchronizeFile(file1, file2)
        // Expect a hard link
        assertThat(compareFileContents(file1,file2))
            .isEqualTo(SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER)
        file1.writeText("content-B")
        // Expect different content
        assertThat(file2.readText()).isEqualTo("content-B")
    }

    @Test
    fun `deleting source file does not delete destination`() {
        val file1 = temporaryFolder.newFile("my-file-1.txt")
        val file2 = temporaryFolder.newFile("my-file-2.txt")
        file1.writeText("content-A")
        synchronizeFile(file1, file2)
        // Expect a hard link
        assertThat(compareFileContents(file1,file2))
            .isEqualTo(SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER)
        file1.delete()
        assertThat(file2.isFile).isTrue()
    }

    @Test
    fun `deleting destination file does not delete source`() {
        val file1 = temporaryFolder.newFile("my-file-1.txt")
        val file2 = temporaryFolder.newFile("my-file-2.txt")
        file1.writeText("content-A")
        synchronizeFile(file1, file2)
        // Expect a hard link
        assertThat(compareFileContents(file1,file2))
            .isEqualTo(SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER)
        file2.delete()
        assertThat(file1.isFile).isTrue()
    }
}
