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
package com.android.testutils.truth

import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

/**
 * Tests for [PathSubject].
 */
class PathSubjectTest {
    private val rootDir = createInMemoryFileSystemAndFolder("test")

    @Test
    fun testExists() {
        val file1 = rootDir.resolve("file1")
        Files.write(file1, listOf("Test content"))
        assertThat(file1).exists()

        val file2 = rootDir.resolve("file2")
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file2).exists()
        }.message).isEqualTo("$file2 expected to exist")
    }

    @Test
    fun testDoesNotExist() {
        val file1 = rootDir.resolve("file1")
        Files.write(file1, listOf("Test content"))
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file1).doesNotExist()
        }.message).isEqualTo("$file1 is not expected to exist")

        val file2 = rootDir.resolve("file2")
        assertThat(file2).doesNotExist()
    }

    @Test
    fun testHasContents() {
        val file = rootDir.resolve("file")
        Files.write(file, listOf("single line"))
        assertThat(file).hasContents("single line")
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file).hasContents("something else")
        }.message).isEqualTo(
                "Not true that <$file> contains <something else>. It is <single line>")

        Files.write(file, listOf("line 1", "line 2"))
        assertThat(file).hasContents("line 1", "line 2")
        assertThat(file).hasContents("line 1\nline 2")
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file).hasContents("line 1", "other")
        }.message).isEqualTo(
                "Not true that <$file> contains <line 1\nother>. It is <line 1\nline 2>")

        Files.write(file, emptyList<String>())
        assertThat(file).hasContents("")
    }

    @Test
    fun testContainsFile() {
        val file1 = rootDir.resolve("dir/file1")
        Files.createDirectories(file1.parent)
        Files.write(file1, listOf("Test content"))
        assertThat(rootDir).containsFile("file1")

        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(rootDir).containsFile("file2")
        }.message).isEqualTo("Directory tree with root at $rootDir is expected to contain file2")
    }

    @Test
    fun testDoesNotContainFile() {
        val file1 = rootDir.resolve("file1")
        Files.write(file1, listOf("Test content"))
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(rootDir).doesNotContainFile("file1")
        }.message).isEqualTo(
                "Directory tree with root at $rootDir is not expected to contain $file1")

        assertThat(rootDir).doesNotContainFile("file2")
    }

    @Test
    fun lastModifiedAndNewerThanTest() {
        val file = rootDir.resolve("myFile")
        Files.write(file, listOf("Test content"))

        val now = FileTime.from(Instant.parse("2018-01-11T12:46:00Z"))
        val tenMinutesAgo = FileTime.from(now.to(TimeUnit.MINUTES) - 10, TimeUnit.MINUTES)

        Files.setLastModifiedTime(file, now)

        assertThat(file).exists()
        assertThat(file).wasModifiedAt(now)
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file).wasModifiedAt(tenMinutesAgo)
        }.message).isEqualTo(
                "Not true that <$file> was last modified at " +
                        "<2018-01-11T12:36:00Z>. " +
                        "It was last modified at <2018-01-11T12:46:00Z>")
        assertThat(file).isNewerThan(tenMinutesAgo)
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file).isNewerThan(now)
        }.message).isEqualTo(
                "Not true that <$file> was modified after " +
                        "<2018-01-11T12:46:00Z>. " +
                        "It was last modified at <2018-01-11T12:46:00Z>")

        Files.setLastModifiedTime(file, tenMinutesAgo)
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file).wasModifiedAt(now)
        }.message).isEqualTo(
                "Not true that <$file> was last modified at " +
                        "<2018-01-11T12:46:00Z>. " +
                        "It was last modified at <2018-01-11T12:36:00Z>")
        assertThat(assertFailsWith(AssertionError::class) {
            assertThat(file).isNewerThan(now)
        }.message).isEqualTo(
                "Not true that <$file> was modified after " +
                        "<2018-01-11T12:46:00Z>. " +
                        "It was last modified at <2018-01-11T12:36:00Z>")
    }
}
