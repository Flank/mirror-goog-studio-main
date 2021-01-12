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

package com.android.build.gradle.internal.cxx.process

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClangMessageCategoryTest {
    private fun shouldElevate(message : String) =
            assertThat(shouldElevateToLifeCycle(message))
                    .named(message)
                    .isTrue()

    private fun shouldNotElevate(message : String) =
            assertThat(shouldElevateToLifeCycle(message))
                    .named(message)
                    .isFalse()

    @Test
    fun checks() {
        shouldElevate("abc.c:1:1: note: There are cats around here")
        shouldElevate("abc.c:1:1: warning: That cat is mean")
        shouldElevate("abc.c:1:1: error: A mean cat bit you")
        shouldNotElevate("abc.c:1:1: snark: I told you the cat was mean")
        shouldElevate("abc.c(1,1): note: There are cats around here")
        shouldElevate("abc.c(1,1): warning: That cat is mean")
        shouldElevate("abc.c(1,1): error: A mean cat bit you")
        shouldNotElevate("abc.c(1,1): snark: Why did you pet the mean cat?")
        shouldNotElevate("ninja: Entering directory `c:\\directory'")
        shouldElevate("clang: error: linker command failed with exit code 1")
        shouldElevate("clang.exe: error: linker command failed with exit code 1")
        shouldElevate("In file included from another-file.cpp:11:")
        assertThat(isNinjaWorkingDirectoryLine("blah")).isFalse()
        assertThat(isNinjaWorkingDirectoryLine("ninja: Entering directory `c:\\directory'")).isTrue()
    }
}
