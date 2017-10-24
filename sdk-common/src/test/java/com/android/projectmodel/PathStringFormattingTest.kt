/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.projectmodel

import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

/**
 * Tests that verify the treatment of non-semantic information in [PathString]
 */
class PathStringFormattingTest {
    fun verifyEquals(obj1: Any, obj2: Any) {
        Truth.assertThat(obj1).isEqualTo(obj2)
        Truth.assertThat(obj1.hashCode()).isEqualTo(obj2.hashCode())
    }

    @Test
    fun testRawPathPreservesBackSlashes() {
        val backSlashes = PathString(PATH_WITH_BACKSLASHES)
        verifyEquals(backSlashes.rawPath,
                PATH_WITH_BACKSLASHES)
        verifyEquals(backSlashes.portablePath,
                PATH_WITH_FORWARD_SLASHES)
        verifyEquals(backSlashes.nativePath,
                PATH_WITH_FORWARD_SLASHES.replace('/', File.separatorChar))
    }

    @Test
    fun testRawPathPreservesForwardSlashes() {
        val backSlashes = PathString(PATH_WITH_FORWARD_SLASHES)
        verifyEquals(backSlashes.rawPath,
                PATH_WITH_FORWARD_SLASHES)
        verifyEquals(backSlashes.portablePath,
                PATH_WITH_FORWARD_SLASHES)
        verifyEquals(backSlashes.nativePath,
                PATH_WITH_FORWARD_SLASHES.replace('/', File.separatorChar))
    }

    @Test
    fun testParentDoesntNormalizeSeparators() {
        verifyEquals(PathString("\\foo\\bar").parent!!.rawPath, "\\foo")
        verifyEquals(PathString("/foo/bar").parent!!.rawPath, "/foo")
    }

    @Test
    fun testVfsPathPreservesTrailingSeparator() {
        val path = PathString("/foo/bar/")
        verifyEquals(path.rawPath, "/foo/bar/")
        verifyEquals(path.parent!!.rawPath, "/foo")
    }

    @Test
    fun testVfsPathDoesntInsertTrailingSeparator() {
        val path = PathString("/foo/bar")
        verifyEquals(path.rawPath, "/foo/bar")
        val parent = path.parent
        verifyEquals(path.parent!!.rawPath, "/foo")
    }

    companion object {
        val PATH_WITH_BACKSLASHES = "\\foo\\bar"
        val PATH_WITH_FORWARD_SLASHES = "/foo/bar"
    }
}

