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

package com.android.build.gradle.internal.cxx.os

import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OsBehaviorTests {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `which on Linux`() {
        val setup = setupFolders(
            platform = PLATFORM_LINUX,
            "folder1/my-exe", "folder2/my-exe")
        assertThat(setup.which("my-exe")).isEqualTo("folder1/my-exe")
    }

    @Test
    fun `which on Linux finds on second path`() {
        val setup = setupFolders(
            platform = PLATFORM_LINUX,
            "folder1/my-other-exe", "folder2/my-exe")
        assertThat(setup.which("my-exe")).isEqualTo("folder2/my-exe")
    }

    @Test
    fun `which on Windows with no extension in paths`() {
        val setup = setupFolders(
            platform = PLATFORM_WINDOWS,
            "folder1/my-other-exe", "folder2/my-exe")
        assertThat(setup.which("my-exe")).isNull()
    }

    @Test
    fun `which on Windows finds BAT`() {
        val setup = setupFolders(
            platform = PLATFORM_WINDOWS,
            "folder1/my-exe.BAT", "folder2/my-exe.BAT")
        assertThat(setup.which("my-exe")).isEqualTo("folder1/my-exe.BAT")
    }

    @Test
    fun `which on Windows finds on second path`() {
        val setup = setupFolders(
            platform = PLATFORM_WINDOWS,
            "folder1/my-exe.EXE", "folder2/my-exe.EXE")
        assertThat(setup.which("my-exe")).isEqualTo("folder1/my-exe.EXE")
    }

    @Test
    fun `which on Windows finds BAT with extension`() {
        val setup = setupFolders(
            platform = PLATFORM_WINDOWS,
            "folder1/my-exe.BAT", "folder2/my-exe.BAT")
        assertThat(setup.which("my-exe.BAT")).isEqualTo("folder1/my-exe.BAT")
    }

    @Test
    fun `which on Windows finds BAT in second path`() {
        val setup = setupFolders(
            platform = PLATFORM_WINDOWS,
            "folder1/my-other-exe.BAT", "folder2/my-exe.BAT")
        assertThat(setup.which("my-exe.BAT")).isEqualTo("folder2/my-exe.BAT")
    }

    private data class TestFolders(
        val os : OsBehavior,
        val base : File,
    ) {
        fun which(executable : String) : String? {
            val result = os.which(File(executable)) ?: return null
            return result.relativeTo(base).path.replace("\\", "/")
        }
    }
    private fun setupFolders(
        platform : Int,
        vararg files : String) : TestFolders {
        val base = tempFolder.newFolder()

        val paths = mutableListOf<File>()

        for(path in files) {
            val file = base.resolve(path)
            file.parentFile.mkdirs()
            paths.add(file.parentFile)
            file.createNewFile()
        }

        val os = createOsBehavior(
            platform = platform,
            environmentPaths = paths
        )
        return TestFolders(
            os = os,
            base = base
        )
    }
}
