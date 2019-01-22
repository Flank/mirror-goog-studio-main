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

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PathMapTest(val mapType: PathMapType) {
    /**
     * Use the test's parameterized value to construct a [PathMap] of the appropriate type.
     */
    fun <T> buildMap(input: Map<PathString, T>): PathMap<T> {
        when (mapType) {
            PathMapType.PATH_HASH_MAP -> return input.toPathMap()
            PathMapType.PATH_TREE_MAP -> return input.toPathTreeMap()
        }
    }

    @Test
    fun testSamePathOnDifferentFilesystemsIsConsideredDifferent() {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val somePath = fs.getPath("tempFile/foo")
        val jimPath = PathString(somePath)
        val localPath = PathString("tempFile/foo")

        val map = buildMap(mapOf(jimPath to "jim", localPath to "local"))

        Truth.assertThat(map[jimPath]).isEqualTo("jim")
        Truth.assertThat(map[localPath]).isEqualTo("local")
    }

    @Test
    fun testEmptyMap() {
        val map = buildMap(mapOf<PathString, PathString>())

        Truth.assertThat(map.values.toList().isEmpty())
        Truth.assertThat(map.findAllStartingWith(PathString("/")).toList().isEmpty()).isTrue()
        Truth.assertThat(map.containsKeyStartingWith(PathString(""))).isFalse()
        Truth.assertThat(map[PathString("foo")]).isNull()
    }

    @Test
    fun testValues() {
        val paths = arrayOf(
            "",
            "/",
            "C:",
            "C:\\",
            "D:",
            "relativePath",
            "/foo/bar",
            "/foo/baz"
        ).map { PathString(it) }

        val map = buildMap(paths.map { it to it }.toMap())

        Truth.assertThat(map.values.toSet()).isEqualTo(paths.toSet())
    }

    @Test
    fun testGet() {
        val paths = arrayOf(
            "",
            "/",
            "C:",
            "C:\\",
            "D:",
            "relativePath",
            "/foo/bar",
            "/foo/baz"
        ).map { PathString(it) }
        val map = buildMap(paths.map { it to it }.toMap())

        paths.forEach {
            Truth.assertWithMessage("map[${it}] = ${it}").that(map[it]).isEqualTo(it)
        }
        Truth.assertThat(map[PathString("Not present")]).isNull()
        Truth.assertThat(map[PathString("/foo")]).isNull()
        Truth.assertThat(map[PathString("/foo/bar")]).isNotNull()
        Truth.assertThat(map[PathString("/foo/bar/bing")]).isNull()
        Truth.assertThat(map[PathString("/foo/bar/bing/bong")]).isNull()
    }

    @Test
    fun testMostSpecificPath() {
        val inputTree = arrayOf(
            "/foo" to "foo",
            "/foo/bar" to "bar",
            "/foo/bar/baz/bing" to "bing",
            "foo" to "relativePath"
        )
        val map = buildMap(inputTree.map { PathString(it.first) to it.second }.toMap())

        val expected = arrayOf(
            "/foo" to "foo",
            "/foo/bang" to "foo",
            "/foo/bar" to "bar",
            "/foo/bar/baz" to "bar",
            "/foo/bang/baz" to "foo",
            "/foo/bar/baz/bing" to "bing",
            "foo" to "relativePath",
            "boo" to null,
            "/boo" to null
        )

        expected.forEach {
            Truth.assertWithMessage("map.findMostSpecific(${it.first}) == ${it.second}")
                .that(map.findMostSpecific(PathString(it.first))).isEqualTo(it.second)
        }
    }

    @Test
    fun testEntriesStartingWith() {
        val inputTree = arrayOf(
            "/a",
            "/a/1",
            "/a/1/x",
            "/a/1/y",
            "/a/2",
            "/a/3",
            "/b",
            "/c/1/2/3/4/5",
            "/c/1/x"
        )

        val map = buildMap(inputTree.map { PathString(it) }.map { it to it }.toMap())

        val expected = arrayOf(
            "/a" to listOf("/a", "/a/1", "/a/1/x", "/a/1/y", "/a/2", "/a/3"),
            "a" to listOf(),
            "/a/1" to listOf("/a/1", "/a/1/x", "/a/1/y"),
            "/a/1/" to listOf("/a/1", "/a/1/x", "/a/1/y"),
            "/b" to listOf("/b"),
            "/c/1/x" to listOf("/c/1/x"),
            "/c/1/y" to listOf(),
            "/c/1/2" to listOf("/c/1/2/3/4/5"),
            "/x" to listOf()
        )

        expected.forEach {
            val containsAny = !it.second.isEmpty()

            listOf<String>().asSequence().toList()

            val results = map.findAllStartingWith(PathString(it.first)).toSet()
            val expectedResults = it.second.map { PathString(it) }.toSet()

            Truth.assertWithMessage("map.findAllStartingWith(${it.first}) == ${it.second}")
                .that(results).isEqualTo(expectedResults)
            Truth.assertWithMessage("map.containsKeyStartingWith(${it.first}) == ${containsAny}")
                .that(map.containsKeyStartingWith(PathString(it.first))).isEqualTo(containsAny)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testParameters(): Array<PathMapType> {
            return PathMapType.values()
        }
    }
}
