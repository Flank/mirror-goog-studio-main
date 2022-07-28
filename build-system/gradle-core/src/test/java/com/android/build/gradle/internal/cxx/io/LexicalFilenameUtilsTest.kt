/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class LexicalFilenameUtils {

    @Test
    fun `check extension`() {
        checkExtension("a.b")
        checkExtension("filename.txt")
        checkExtension("filename.c")
        checkExtension("filename.C")
        checkExtension("file.name.cpp")
        checkExtension(".cpp")
        checkExtension(".")
        checkExtension("")
    }

    @Test
    fun `check blank extension`() {
        checkExtension("")
    }

    @Test
    fun `check extension when folder has dot`() {
        checkExtension("folder.something/filename.txt")
        checkExtension("folder.something\\filename.txt")
    }

    @Test
    fun `check extension when trailing slash folder`() {
        checkExtension("a.b/")
    }

    @Test
    fun `check hasExtension`() {
        checkHasExtension("filename.txt", "txt")
        checkHasExtension("filename.c", "c")
        checkHasExtension("filename.C", "c")
        checkHasExtension("file.name.cpp", "CPP")
        checkHasExtension(".cpp", "cPp")
        checkHasExtension(".", "")
        checkHasExtension("", "")
    }

    @Test
    fun `check filename ends with`() {
        checkFilenameEndsWith("ar.exe", "AR")
        checkFilenameEndsWith("clang", "clang")
        checkFilenameEndsWith("x86-clang", "clang")
        checkFilenameEndsWith("/x86-clang", "clang")
    }

    @Test
    fun `check filename starts with`() {
        checkFilenameStartsWith("ar.exe", "AR")
        checkFilenameStartsWith("ar.exe", "AR.")
        checkFilenameStartsWith("clang", "clang")
        checkFilenameStartsWith("x86-clang", "x86")
        checkFilenameStartsWith("/x86-clang", "x86")
    }

    @Test
    fun `check remove extension`() {
        assertThat("filename.passthrough".removeExtensionIfPresent("passthrough")).isEqualTo("filename")
        assertThat("filename.PASSTHROUGH".removeExtensionIfPresent("passthrough")).isEqualTo("filename")
        assertThat("filename.exe".removeExtensionIfPresent("passthrough")).isEqualTo("filename.exe")
    }

    private fun checkExtension(filename : LexicalFilename) {
        val expected = File(filename.toString()).extension
        checkHasExtension(filename, expected)
    }

    private fun checkHasExtension(filename : LexicalFilename, extension : String) {
        assertThat(filename.hasExtensionIgnoreCase(extension)).isTrue()
        assertThat(filename.hasExtensionIgnoreCase(extension, ".extra.")).isTrue()
    }

    private fun checkFilenameStartsWith(filename : LexicalFilename, ending : String) {
        assertThat(filename.filenameStartsWithIgnoreCase(ending)).isTrue()
    }

    private fun checkFilenameEndsWith(filename : LexicalFilename, start : String) {
        assertThat(filename.filenameEndsWithIgnoreCase(start)).isTrue()
    }
}
