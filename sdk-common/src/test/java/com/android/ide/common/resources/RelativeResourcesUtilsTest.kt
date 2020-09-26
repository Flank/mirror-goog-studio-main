/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ide.common.resources

import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException

/**
 * Tests for [com.android.ide.common.resources.RelativeResourceUtils].
 */
class RelativeResourcesUtilsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `test should convert absolute path to relative path format`() {
        val testAbsolutePath = FileUtils.join(
                "usr", "a", "b", "myproject", "app", "src", "main",
                "res", "layout", "activity_map_tv.xml")
        val testAbsoluteFile = File(testAbsolutePath)
        val packageName = "com.foobar.myproject.app"
        val sourceSets = listOf(
                listOf(File(FileUtils.join("usr", "a", "b", "myproject", "app", "src", "main", "res"))),
                listOf(File(FileUtils.join("usr", "a", "b", "myproject", "app", "src", "debug", "res")))
        )

        val expected = produceRelativeSourceSetPath(testAbsoluteFile, packageName, sourceSets)
        assertThat(expected).isEqualTo("com.foobar.myproject.app-0:/layout/activity_map_tv.xml")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test should throw IllegalArgmentException if file is not contained in source sets`() {
        val testAbsolutePath = FileUtils.join("myproject", "app", "src", "main",
                "res", "layout", "activity_map_tv.xml")
        val testAbsoluteFile = File(testAbsolutePath)
        val packageName = "com.foobar.myproject.app"
        val sourceSets = listOf(
                listOf(File(FileUtils.join("myproject", "app", "src", "custom", "res"))),
                listOf(File(FileUtils.join("myproject", "app", "src", "debug", "res")))
        )

        val expected = produceRelativeSourceSetPath(testAbsoluteFile, packageName, sourceSets)
        assertThat(expected).isEqualTo("com.foobar.myproject.app-0:res/layout/activity_map_tv.xml")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test should cause exception if path does not contains a valid source set`() {
        val testAbsolutePath = FileUtils.join(
                "usr", "a", "b", "myproject", "app", "src", "main", "layout", "activity_map_tv.xml")
        val testAbsoluteFile = File(testAbsolutePath)

        val packageName = "com.foobar.myproject.app"
        val sourceSets = listOf(
                listOf(
                    File(FileUtils.join(
                                    "usr", "a", "b", "myproject", "app", "src", "main", "res"))
                )
        )

        produceRelativeSourceSetPath(testAbsoluteFile, packageName, sourceSets)
    }

    @Test
    fun `test should accept merged dot dir as a source set`() {
        val testAbsolutePath = FileUtils.join(
                "usr", "a", "b", "myproject", "build", "intermediates",
                "incremental", "mergeDebugResources", "merged.dir", "layout", "activity_map_tv.xml")
        val testAbsoluteFile = File(testAbsolutePath)
        val packageName = "com.foobar.myproject.app"
        val sourceSets = listOf<List<File>>()

        val relativePath =
                produceRelativeSourceSetPath(testAbsoluteFile, packageName, sourceSets)
        assertThat(relativePath).isEqualTo(
                "com.foobar.myproject.app-incremental-mergeDebugResources-merged.dir:/layout/activity_map_tv.xml"
        )
    }

    @Test
    fun `test should handle generated pngs`() {
        // Generated pngs are not included within resource sets, therefore they are
        // identified using $packagename-pngs$variant
        val testAbsolutePath = FileUtils.join(
                "usr", "a", "b", "myproject", "build", "generated", "res",
                "pngs", "debug", "drawable", "a.png")
        val testAbsoluteFile = File(testAbsolutePath)

        val packageName = "com.foobar.myproject.app"
        val result = produceRelativeSourceSetPath(testAbsoluteFile, packageName, emptyList())
        assertThat(result)
                .isEqualTo("com.foobar.myproject.app-generated-pngs-debug:/drawable/a.png")
    }

    @Test
    fun `test should convert relative path format to absolute path format`() {
        val sourceSetPathMap =
                mapOf("com.foobar.myproject.app-0" to "/usr/a/b/c/d/myproject/src/main/")
        val testRelativePath = "com.foobar.myproject.app-0:res/layout/activity_map_tv.xml"
        val expectedAbsolutePath = "/usr/a/b/c/d/myproject/src/main/res/layout/activity_map_tv.xml"

        assertThat(relativeResourcePathToAbsolutePath(testRelativePath, sourceSetPathMap))
                .isEqualTo(expectedAbsolutePath)
    }

    @Test(expected = IllegalStateException::class)
    fun `test should throw IllegalStateException if root map contains no paths`() {
        val testRelativePath = "com.foobar.myproject.app-0:res/layout/activity_map_tv.xml"
        relativeResourcePathToAbsolutePath(testRelativePath, emptyMap())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test should throw IllegalArgumentException if no colon separator in relative path`() {
        val sourceSetPathMap =
                mapOf("com.foobar.myproject.app-0" to "/usr/a/b/c/d/myproject/src/main/")
        // Path does not contain ':' separator.
        val invalidRelativePath = "com.foobar.myproject.app-0res/layout/activity_map_tv.xml"
        relativeResourcePathToAbsolutePath(invalidRelativePath, sourceSetPathMap)
    }

    @Test(expected = NoSuchElementException::class)
    fun `test should throw NoSuchElementException if there is no matching id to absolute path`() {
        val sourceSetPathMap =
                mapOf("com.foobar.myproject.app-0" to "/usr/a/b/c/d/myproject/src/main/")
        val invalidRelativePath = "invalid-id:res/layout/activity_map_tv.xml"
        relativeResourcePathToAbsolutePath(invalidRelativePath, sourceSetPathMap)
    }

    @Test
    fun `test should load source set map file to map`() {
        val sourceSetPathsMapDir = File(temporaryFolder.newFolder(), "test").also { it.mkdir() }
        val sourceSetPathsMapFile = File(sourceSetPathsMapDir, "file-path.txt").also {
            it.writeText("com.foobar.myproject.app-0 /usr/a/b/c/d/myproject/src/main/\n")
        }
        val sourceSetPathsMap = readFromSourceSetPathsFile(sourceSetPathsMapFile)
        assertThat(sourceSetPathsMap)
                .isEqualTo(
                        mapOf(
                                "com.foobar.myproject.app-0" to "/usr/a/b/c/d/myproject/src/main/"
                        )
                )
    }

    @Test(expected = IOException::class)
    fun `test should throw error if mapping file does not exist`() {
        val sourceSetPathsMapDir = File(temporaryFolder.newFolder(), "test").also { it.mkdir() }
        val sourceSetPathsMapFile = File(sourceSetPathsMapDir, "file-path.txt")
        readFromSourceSetPathsFile(sourceSetPathsMapFile)
    }
}