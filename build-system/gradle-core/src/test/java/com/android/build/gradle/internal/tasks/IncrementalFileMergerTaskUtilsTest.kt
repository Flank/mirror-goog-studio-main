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

package com.android.build.gradle.internal.tasks

import com.android.builder.files.FileCacheByPath
import com.android.ide.common.resources.FileStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IncrementalFileMergerTaskUtilsTest {

    @get:Rule
    var tmpDir = TemporaryFolder()

    private lateinit var jarInput: File
    private lateinit var dirInput: File
    private lateinit var dirFileFoo: File
    private lateinit var dirFileBar: File
    private lateinit var changedInputs: MutableMap<File, FileStatus>
    private lateinit var zipCache: FileCacheByPath
    private lateinit var cacheUpdates: MutableList<Runnable>

    @Before
    fun setUp() {
        jarInput = File(tmpDir.root, "jarInput.jar")
        dirInput = tmpDir.newFolder("dirInput")
        dirFileFoo = File(dirInput, "foo.txt")
        dirFileBar = File(dirInput, "bar.txt")
        changedInputs = mutableMapOf()
        zipCache = FileCacheByPath(tmpDir.newFolder("cache"))
        cacheUpdates = mutableListOf()

        ZipOutputStream(FileOutputStream(jarInput)).use { zip ->
            var e = ZipEntry("foo")
            zip.putNextEntry(e)
            zip.write("foo".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            e = ZipEntry("bar")
            zip.putNextEntry(e)
            zip.write("bar".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        dirFileFoo.printWriter().use { it.println("foo") }
        dirFileBar.printWriter().use { it.println("bar") }
    }

    @Test
    fun `test toIncrementalInput with new jar input`() {
        changedInputs[jarInput] = FileStatus.NEW

        val result = toIncrementalInput(jarInput, changedInputs, zipCache, cacheUpdates)
        assertThat(result.allPaths).containsExactly("foo", "bar")
        assertThat(result.updatedPaths).containsExactly("foo", "bar")
    }

    @Test
    fun `test toIncrementalInput with changed jar input`() {
        toNonIncrementalInput(jarInput, zipCache, cacheUpdates)
        cacheUpdates.forEach(Runnable::run)

        // recreate jar without extra baz entry
        ZipOutputStream(FileOutputStream(jarInput)).use { zip ->
            var e = ZipEntry("foo")
            zip.putNextEntry(e)
            zip.write("foo".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            e = ZipEntry("bar")
            zip.putNextEntry(e)
            zip.write("bar".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            e = ZipEntry("baz")
            zip.putNextEntry(e)
            zip.write("baz".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        changedInputs[jarInput] = FileStatus.CHANGED

        val result = toIncrementalInput(jarInput, changedInputs, zipCache, cacheUpdates)
        assertThat(result.allPaths).containsExactly("foo", "bar", "baz")
        assertThat(result.updatedPaths).containsExactly("baz")
    }

    @Test
    fun `test toIncrementalInput with deleted jar input`() {
        toNonIncrementalInput(jarInput, zipCache, cacheUpdates)
        cacheUpdates.forEach(Runnable::run)

        changedInputs[jarInput] = FileStatus.REMOVED
        jarInput.delete()

        val result = toIncrementalInput(jarInput, changedInputs, zipCache, cacheUpdates)
        assertThat(result.allPaths).isEmpty()
        assertThat(result.updatedPaths).containsExactly("foo", "bar")
    }

    @Test
    fun `test toIncrementalInput with new dir input`() {
        changedInputs[dirFileFoo] = FileStatus.NEW
        changedInputs[dirFileBar] = FileStatus.NEW

        val result = toIncrementalInput(dirInput, changedInputs, zipCache, cacheUpdates)
        assertThat(result.allPaths).containsExactly("foo.txt", "bar.txt")
        assertThat(result.updatedPaths).containsExactly("foo.txt", "bar.txt")
    }

    @Test
    fun `test toIncrementalInput with changed dir input`() {
        toNonIncrementalInput(dirInput, zipCache, cacheUpdates)

        dirFileFoo.printWriter().use { it.println("foofoo") }

        changedInputs[dirFileFoo] = FileStatus.CHANGED

        val result = toIncrementalInput(dirInput, changedInputs, zipCache, cacheUpdates)
        assertThat(result.allPaths).containsExactly("foo.txt", "bar.txt")
        assertThat(result.updatedPaths).containsExactly("foo.txt")
    }

    @Test
    fun `test toIncrementalInput with deleted dir input`() {
        toNonIncrementalInput(jarInput, zipCache, cacheUpdates)

        changedInputs[dirFileFoo] = FileStatus.REMOVED
        dirFileFoo.delete()

        val result = toIncrementalInput(dirInput, changedInputs, zipCache, cacheUpdates)
        assertThat(result.allPaths).containsExactly("bar.txt")
        assertThat(result.updatedPaths).containsExactly("foo.txt")
    }

    @Test
    fun `test toNonIncrementalInput with jar input`() {
        val result = toNonIncrementalInput(jarInput, zipCache, cacheUpdates)
        assertThat(result).isNotNull()
        assertThat(result?.allPaths).containsExactly("foo", "bar")
        assertThat(result?.updatedPaths).containsExactly("foo", "bar")
    }

    @Test
    fun `test toNonIncrementalInput with dir input`() {
        val result = toNonIncrementalInput(dirInput, zipCache, cacheUpdates)
        assertThat(result).isNotNull()
        assertThat(result?.allPaths).containsExactly("foo.txt", "bar.txt")
        assertThat(result?.updatedPaths).containsExactly("foo.txt", "bar.txt")
    }
}