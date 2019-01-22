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
 * Tests that apply to all implementations of [MutablePathMap]
 */
class MutablePathMapTest {
    /**
     * If we ever create a second implementation of [MutablePathMap], this test suite should become
     * parameterized for the different implementations and this method can return the appropriate
     * map type.
     */
    fun <T> createMap(): MutablePathMap<T> {
        return pathTreeMapOf()
    }

    @Test
    fun testPut() {
        val map = createMap<String>()

        assertThat(map.values.toList().isEmpty()).isTrue()

        map.put(PathString("/foo/bar"), "bar")

        assertThat(map.values.toList().isEmpty()).isFalse()

        map.put(PathString("/foo/baz"), "baz")
        map.put(PathString("/bing"), "bing")

        assertThat(map.values.toSet()).isEqualTo(setOf("bar", "baz", "bing"))
        assertThat(map.findMostSpecific(PathString("/foo/bar/bozzel"))).isEqualTo("bar")
        assertThat(map.findAllStartingWith(PathString("/foo")).toList().size).isEqualTo(2)
    }
}
