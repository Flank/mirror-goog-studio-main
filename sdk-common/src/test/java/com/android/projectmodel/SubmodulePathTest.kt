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

package com.android.projectmodel

import com.google.common.truth.Truth
import org.junit.Test

/**
 * Tests for [SubmodulePath].
 */
class SubmodulePathTest {
    private val empty = submodulePathForString("")
    private val foo = submodulePathForString("foo")
    private val fooBarBaz = submodulePathForString("foo/bar/baz")

    /**
     * Tests that the "simpleName" method produces correct strings in the style of Gradle
     * variants.
     */
    @Test
    fun testSimpleName() {
        val expected = listOf(
            empty to "main",
            foo to "foo",
            fooBarBaz to "fooBarBaz"
        )

        for (next in expected) {
            Truth.assertThat(next.first.simpleName).isEqualTo(next.second)
        }
    }

    @Test
    fun testSubmodulePathOf() {
        Truth.assertThat(submodulePathOf()).isEqualTo(emptySubmodulePath)
        Truth.assertThat(submodulePathOf("foo")).isEqualTo(submodulePathForString("foo"))
        Truth.assertThat(submodulePathOf("foo", "bar")).isEqualTo(submodulePathForString("foo/bar"))
    }

    @Test
    fun testLastSegment() {
        val expected = listOf(
            empty to "",
            foo to "foo",
            fooBarBaz to "baz"
        )

        for (next in expected) {
            Truth.assertThat(next.first.lastSegment).isEqualTo(next.second)
        }
    }

    @Test
    fun testToString() {
        val expected = listOf(
            empty to "",
            foo to "foo",
            fooBarBaz to "foo/bar/baz"
        )

        for (next in expected) {
            Truth.assertThat(next.first.toString()).isEqualTo(next.second)
        }
    }
}
