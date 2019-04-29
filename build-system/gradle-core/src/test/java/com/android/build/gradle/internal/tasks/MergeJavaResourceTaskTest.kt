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

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Test cases for [MergeJavaResourceTask].  */
class MergeJavaResourceTaskTest {

    @get:Rule
    var tmpDir = TemporaryFolder()

    @Test
    fun testGetRelativePaths() {
        val root = tmpDir.newFolder()
        val dir1 = File(root, "dir1")
        val dir2 = File(root, "dir2")
        val dir3 = File(root, "foo/dir3")
        val dir4 = File(root, "dir2/dir4")
        val dirs = listOf(dir1, dir2, dir3, dir4)
        val files =
            listOf(
                File(dir1, "a"),
                File(dir1, "a/b"),
                File(dir1, "a/b/c"),
                File(dir2, "a"),
                File(dir2, "d/e"),
                File(dir3, "f"),
                File(dir4, "g")
            )
        val expectedRelativePaths = listOf("a", "a", "a/b", "a/b/c", "d/e", "f", "g")
        assertThat(getRelativePaths(files, dirs)).isEqualTo(expectedRelativePaths)
    }

    @Test
    fun testGetRelativePathsNoFiles() {
        val root = tmpDir.newFolder()
        val dir1 = File(root, "dir1")
        val dirs = listOf(dir1)
        val files = listOf<File>()
        val expectedRelativePaths = listOf<String>()
        assertThat(getRelativePaths(files, dirs)).isEqualTo(expectedRelativePaths)
    }

    @Test
    fun testGetRelativePathsNoFilesOrDirs() {
        val dirs = listOf<File>()
        val files = listOf<File>()
        val expectedRelativePaths = listOf<String>()
        assertThat(getRelativePaths(files, dirs)).isEqualTo(expectedRelativePaths)
    }
}

