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

import com.google.common.truth.Truth
import org.junit.Test
import pathTreeMapOf
import toPathTreeMap

/**
 * Tests that apply to [PathTreeMapImpl] but not to other [MutablePathMap] implementations. For
 * additional test cases, see [MutablePathMapTest].
 */
class PathTreeMapTest {
    @Test
    fun testPathTreeMapOfContainsCorrectInitialContents() {
        val map = pathTreeMapOf(
            PathString("/foo/bar") to "bar",
            PathString("/wakka/wakka") to "wakka"
        )
        Truth.assertThat(map[PathString("/foo/bar")]).isEqualTo("bar")
    }

    @Test
    fun testToPathTreeMapMakesACopy() {
        val foo = PathString("foo")
        val bar = PathString("foo")

        val input = hashMapOf(foo to "foo")
        val map = input.toPathTreeMap()
        Truth.assertThat(input[foo]).isEqualTo("foo")
        Truth.assertThat(map[foo]).isEqualTo("foo")

        // Modify the input map and confirm it doesn't affect the tree map
        input.clear()

        Truth.assertThat(input[foo]).isNull()
        Truth.assertThat(map[foo]).isEqualTo("foo")

        // Modify the tree map and confirm it doesn't affect the input map
        map.put(bar, "bar")

        Truth.assertThat(input[bar]).isNull()
        Truth.assertThat(map[bar]).isEqualTo("bar")
    }
}
