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
package com.android.ide.common.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests that verify the hashcode and equals behavior for [PathString]
 */
class PathStringEqualityTest {
    @Test
    fun testPathsWithCosmeticDifferencesNotEqual() {
        assertThat(PathString("/foo")).isNotEqualTo(PathString("\\foo"))
        assertThat(PathString("/foo/")).isNotEqualTo(PathString("/foo"))
    }

    @Test
    fun testPathEqualsItself() {
        val path = PathString("/foo")
        assertThat(path).isEqualTo(path)
    }

    @Test
    fun testIdenticalPathsConstructedFromDifferentSubstringsAreEqual() {
        val fooBarBazBar = PathString("/foo/bar/baz/bar")
        val fooBarBaz = PathString("/foo/bar/baz")

        assertThat(fooBarBazBar.parent).isEqualTo(fooBarBaz)
        assertThat(fooBarBazBar[1]).isEqualTo(PathString("bar"))
        assertThat(fooBarBaz.relativize(fooBarBazBar)).isEqualTo(PathString("bar"))
    }

    @Test
    fun testPathsWithEqualStringsAreEqual() {
        val path1 = PathString("C:\\Program Files\\My App\\someprogram.exe")
        val path2 = PathString("C:\\Program Files\\My App\\someprogram.exe")
        assertThat(path1).isEqualTo(path2)
    }
}