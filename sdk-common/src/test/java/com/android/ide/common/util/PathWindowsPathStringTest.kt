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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests that verify the hashcode and equals behavior for [PathString]
 */
class PathWindowsPathStringTest {
    @Test
    fun testAbsoluteLocalPath() {
        val path = PathString("C:\\Program Files\\My App\\someprogram.exe")
        assertThat(path.root!!.rawPath).isEqualTo("C:\\")
        assertThat(path.isAbsolute).isTrue()
        assertThat(path.root!!.isAbsolute).isTrue()
        assertThat(path.parent).isEqualTo(PathString("C:\\Program Files\\My App"))
        assertThat(path.fileName).isEqualTo("someprogram.exe")
        assertThat(path[0]).isEqualTo(PathString("Program Files"))
        assertThat(path[1]).isEqualTo(PathString("My App"))
        assertThat(path.subpath(1, 3)).isEqualTo(PathString("My App\\someprogram.exe"))
        assertThat(path.subpath(0, 2)).isEqualTo(PathString("Program Files\\My App"))
    }

    @Test
    fun testRelativeLocalPathWithRoot() {
        val path = PathString("C:Program Files\\My App\\someprogram.exe")
        assertThat(path.root!!.rawPath).isEqualTo("C:")
        assertThat(path.isAbsolute).isFalse()
        assertThat(path.root!!.isAbsolute).isFalse()
    }

    @Test
    fun testRelativeLocalPathWithoutRoot() {
        val path = PathString("Program Files\\My App\\someprogram.exe")
        assertThat(path.root).isNull()
        assertThat(path.isAbsolute).isFalse()
    }

    @Test
    fun resolveRootlessRelPathAgainstAbsPath() {
        val resolved = PathString("C:\\foo").resolve(PathString("bar\\baz"))
        assertThat(resolved).isEqualTo(PathString("C:\\foo\\bar\\baz"))
    }

    @Test
    fun resolveRootRelPathAgainstAbsPath() {
        val resolved = PathString("C:\\foo").resolve(PathString("bar\\baz"))
        assertThat(resolved).isEqualTo(PathString("C:\\foo\\bar\\baz"))
    }
}