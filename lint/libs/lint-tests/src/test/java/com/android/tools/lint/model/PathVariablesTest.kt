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

package com.android.tools.lint.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.File.separator

class PathVariablesTest {
    /**
     * For a given [file], make sure that with the given path
     * [variables] it will serialize to the given [path],
     * and deserialize from that path to the given file.
     */
    private fun check(variables: PathVariables, file: File, path: String) {
        assertEquals(path, variables.toPathString(file, unix = true))
        assertEquals(file, variables.fromPathString(path))
    }

    @Test
    fun testMatching() {
        val home = File(System.getProperty("user.home"))
        val temp = File(System.getProperty("java.io.tmpdir"))
        val underHome = File(home, "sub")
        assertTrue(home.isDirectory)
        assertTrue(temp.isDirectory)

        val desc = "\$SUB=$underHome;\$HOME=$home;\$TEMP=$temp;"
        val variables = PathVariables.parse(desc)

        val inHome = File(home, "dir1" + separator + "dir2")
        check(variables, inHome, "\$HOME/dir1/dir2")

        val inHomeSpecific = File(underHome, "dir1" + separator + "dir2")
        check(variables, inHomeSpecific, "\$SUB/dir1/dir2")

        val inTemp = File(temp, "file")
        check(variables, inTemp, "\$TEMP/file")

        check(variables, home, "\$HOME")
        assertEquals("\$HOME", variables.toPathString(home))
        assertEquals("\$HOME/", variables.toPathString(home) + separator)
    }

    @Test
    fun testRelativeTo() {
        val home = File(System.getProperty("user.home"))
        val underHome = File(home, "dir1" + separator + "dir2")
        val temp = File(System.getProperty("java.io.tmpdir"))
        val underTemp = File(temp, "dir1/dir2")
        val other = File("/other/path")

        val variables = PathVariables()
        variables.add("HOME", home)
        // temp and other deliberately not added; we want to check relative and absolute path handling

        assertEquals("\$HOME/dir1/dir2", variables.toPathString(underHome, relativeTo = home, unix = true))
        assertEquals(
            "\$HOME${separator}dir1${separator}dir2",
            variables.toPathString(underHome, relativeTo = home, unix = false)
        )

        assertEquals(underHome, variables.fromPathString("dir1/dir2", relativeTo = home))
        assertEquals(underHome, variables.fromPathString("dir1${separator}dir2", relativeTo = home))

        // If no variable match, use relativeTo anchor
        assertEquals("dir1/dir2", variables.toPathString(underTemp, relativeTo = temp, unix = true))
        assertEquals(
            "dir1${separator}dir2",
            variables.toPathString(underTemp, relativeTo = temp, unix = false)
        )

        assertEquals(underTemp, variables.fromPathString("dir1/dir2", relativeTo = temp))
        assertEquals(underTemp, variables.fromPathString("dir1${separator}dir2", relativeTo = temp))

        assertEquals("/other/path", variables.toPathString(other, relativeTo = home, unix = true))
    }

    @Test
    fun testMerge() {
        val variables = PathVariables()
        assertFalse(variables.any())
        variables.add("FIRST", File("f1"))
        assertTrue(variables.any())
        variables.add("SECOND", File("f2o"))

        val other = PathVariables()
        other.add("SECOND", File("f2"))
        other.add("THIRD", File("f3"))

        variables.add(other)
        assertEquals(
            "FIRST=f1\n" +
                "SECOND=f2\n" +
                "THIRD=f3",
            variables.toString()
        )
    }

    @Test
    fun testNormalize() {
        val tempFile = File.createTempFile("prefix", "suffix")
        val temp = tempFile.parentFile ?: return
        val alias = File(temp.path + separator + ".." + separator + temp.name)

        val variables = PathVariables()
        variables.add("TEMP", alias)
        variables.normalize()
        val normalizedFile = File(temp.canonicalFile, "test")
        assertEquals("\$TEMP/test", variables.toPathString(normalizedFile, unix = true))
    }

    // TODO: Test sorting
    @Test
    fun testSorting() {
        val variables = PathVariables()
        with(variables) {
            add("V1", File("foo"))
            add("V8_canonical", File("canonicalized_foo/bar"))
            add("V9_canonical", File("canonicalized_foo"))
            add("V7_canonical", File("canonicalized_foo"))
            add("V2", File("foo/bar"))
            add("V3", File("foo/bar/bax"))
            add("V4", File("foo/bar/bay"))
            add("V5", File("foo/bar/baz"))
            add("V6", File("foo/baa/baz"))
        }
        assertEquals(
            "" +
                "V6=foo/baa/baz\n" +
                "V3=foo/bar/bax\n" +
                "V4=foo/bar/bay\n" +
                "V5=foo/bar/baz\n" +
                "V2=foo/bar\n" +
                "V1=foo\n" +
                "V8_canonical=canonicalized_foo/bar\n" +
                "V7_canonical=canonicalized_foo\n" +
                "V9_canonical=canonicalized_foo",
            variables.toString()
        )
    }
}
