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

/**
 * Tests that verify the hashcode and equals behavior for [PathString]
 */
class PathWindowsPathStringTest {
    @Test
    fun testAbsoluteLocalPath() {
        val path = PathString("C:\\Program Files\\My App\\someprogram.exe")
        Truth.assertThat(path.root!!.rawPath).isEqualTo("C:\\")
        Truth.assertThat(path.isAbsolute).isTrue()
        Truth.assertThat(path.root!!.isAbsolute).isTrue()
        Truth.assertThat(path.parent).isEqualTo(PathString("C:\\Program Files\\My App"))
        Truth.assertThat(path.fileName.rawPath).isEqualTo("someprogram.exe")
        Truth.assertThat(path[0]).isEqualTo(PathString("Program Files"))
        Truth.assertThat(path[1]).isEqualTo(PathString("My App"))
        Truth.assertThat(path.subpath(1, 3)).isEqualTo(PathString("My App\\someprogram.exe"))
        Truth.assertThat(path.subpath(0, 2)).isEqualTo(PathString("Program Files\\My App"))
    }

    @Test
    fun testRelativeLocalPathWithRoot() {
        val path = PathString("C:Program Files\\My App\\someprogram.exe")
        Truth.assertThat(path.root!!.rawPath).isEqualTo("C:")
        Truth.assertThat(path.isAbsolute).isFalse()
        Truth.assertThat(path.root!!.isAbsolute).isFalse()
    }

    @Test
    fun testRelativeLocalPathWithoutRoot() {
        val path = PathString("Program Files\\My App\\someprogram.exe")
        Truth.assertThat(path.root).isNull()
        Truth.assertThat(path.isAbsolute).isFalse()
    }

    @Test
    fun resolveRootlessRelPathAgainstAbsPath() {
        val resolved = PathString("C:\\foo").resolve(PathString("bar\\baz"))
        Truth.assertThat(resolved).isEqualTo(PathString("C:\\foo\\bar\\baz"))
    }

    @Test
    fun resolveRootRelPathAgainstAbsPath() {
        val resolved = PathString("C:\\foo").resolve(PathString("bar\\baz"))
        Truth.assertThat(resolved).isEqualTo(PathString("C:\\foo\\bar\\baz"))
    }
}