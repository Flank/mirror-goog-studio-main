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
package com.android.ide.common.util

import com.google.common.io.Files
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

/**
 * Test cases for [JdkPathOpener].
 */
class JdkPathOpenerTest {
    private lateinit var someFile: PathString
    private lateinit var someFolder: PathString
    private lateinit var nonExistent: PathString
    private lateinit var unknownProtocol: PathString

    @Before
    fun setUp() {
        someFile = PathString(File.createTempFile("fooBar", ".tmp"))
        someFile.toFile()?.printWriter()?.use { it.print("Hello World") }
        someFolder = PathString(Files.createTempDir())
        Truth.assertThat(someFolder.toFile()).isNotNull()
        Truth.assertThat(someFolder.toPath()).isNotNull()
        nonExistent = PathString("nonexistent.file")
        unknownProtocol = PathString("missingprotocol", "nonexistent.file")
    }

    @After
    fun tearDown() {
        someFolder.toFile()?.delete()
        someFile.toFile()?.delete()
    }

    @Test
    fun testIsDirectory() {
        Truth.assertThat(JdkPathOpener.isDirectory(someFile)).isFalse()
        Truth.assertThat(JdkPathOpener.isDirectory(someFolder)).isTrue()
        Truth.assertThat(JdkPathOpener.isDirectory(nonExistent)).isFalse()
        Truth.assertThat(JdkPathOpener.isDirectory(unknownProtocol)).isFalse()
    }

    @Test
    fun testIsRegularFile() {
        Truth.assertThat(JdkPathOpener.isRegularFile(someFile)).isTrue()
        Truth.assertThat(JdkPathOpener.isRegularFile(someFolder)).isFalse()
        Truth.assertThat(JdkPathOpener.isRegularFile(nonExistent)).isFalse()
        Truth.assertThat(JdkPathOpener.isRegularFile(unknownProtocol)).isFalse()
    }

    fun assertThrowsWhenOpened(path: PathString) {
        var thrown = false
        try {
            JdkPathOpener.open(path)
        } catch (e: FileNotFoundException) {
            // Expected
            thrown = true;
        }
        Truth.assertWithMessage("Expecting JdkPathOpener.open(${path}} to throw an IOException")
            .that(thrown).isTrue()
    }

    @Test
    fun testNewInputStream() {
        assertThrowsWhenOpened(nonExistent)
        assertThrowsWhenOpened(unknownProtocol)
        val lines = JdkPathOpener.open(someFile).reader().use { it.readLines() }
        Truth.assertThat(lines).containsExactly("Hello World")

        // Behavior undefined when opening a stream on a folder path. For consistency with NIO,
        // we currently return a stream in this case, but it may make more sense to throw an
        // IOException.

        // assertThrowsWhenOpened(someFolder)
    }
}