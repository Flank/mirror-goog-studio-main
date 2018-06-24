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
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that verify the hashcode and equals behavior for [PathString]
 */
class ConfigPathTest {

    private val none = matchNoArtifacts()
    private val all = matchAllArtifacts()
    private val foo = matchArtifactsWith("foo")
    private val bar = matchArtifactsWith("bar")
    private val fooBarBaz = matchArtifactsWith("foo/bar/baz")
    private val anyBarBaz = matchArtifactsWith("*/bar/baz")
    private val fooBarAny = matchArtifactsWith("foo/bar/*")
    private val fooAnyAny = matchArtifactsWith("foo/*/*")
    private val anyAnyBaz = matchArtifactsWith("*/*/baz")
    private val barBarBaz = matchArtifactsWith("bar/bar/baz")
    private val fooAnyBaz = matchArtifactsWith("foo/*/baz")
    private val barAnyBaz = matchArtifactsWith("bar/*/baz")

    /**
     * Tests that the "simpleName" method produces correct strings in the style of Gradle
     * variants.
     */
    @Test
    fun testSimpleName() {
        val expected = listOf(
            none to "",
            all to "main",
            foo to "foo",
            fooBarBaz to "fooBarBaz",
            anyBarBaz to "barBaz",
            fooBarAny to "fooBar",
            fooAnyAny to "foo",
            anyAnyBaz to "baz",
            fooAnyBaz to "fooBaz",
            matchArtifactsWith("FOO/bAR/bAz/") to "fooBarBaz"
        )

        for (next in expected) {
            Truth.assertThat(next.first.simpleName).isEqualTo(next.second)
        }
    }

    @Test
    fun testMatchesEverything() {
        Truth.assertThat(none.matchesEverything).isFalse()
        Truth.assertThat(all.matchesEverything).isTrue()
        Truth.assertThat(fooAnyBaz.matchesEverything).isFalse()
        Truth.assertThat(fooBarBaz.matchesEverything).isFalse()
    }

    @Test
    fun testMatchArtifactsWith() {
        Truth.assertThat(matchArtifactsWith("")).isEqualTo(none)
        Truth.assertThat(matchArtifactsWith("*")).isEqualTo(all)
    }

    @Test
    fun testMatchesNothing() {
        Truth.assertThat(none.matchesAnything).isFalse()
        Truth.assertThat(all.matchesAnything).isTrue()
        Truth.assertThat(fooAnyBaz.matchesAnything).isTrue()
        Truth.assertThat(fooBarBaz.matchesAnything).isTrue()
    }

    @Test
    fun testIntersect() {
        Truth.assertThat(none.intersect(none)).isEqualTo(none)
        Truth.assertThat(none.intersect(all)).isEqualTo(none)
        Truth.assertThat(none.intersect(foo)).isEqualTo(none)
        Truth.assertThat(none.intersect(bar)).isEqualTo(none)
        Truth.assertThat(none.intersect(fooBarBaz)).isEqualTo(none)
        Truth.assertThat(none.intersect(anyBarBaz)).isEqualTo(none)
        Truth.assertThat(none.intersect(fooBarAny)).isEqualTo(none)
        Truth.assertThat(none.intersect(fooAnyAny)).isEqualTo(none)
        Truth.assertThat(none.intersect(anyAnyBaz)).isEqualTo(none)
        Truth.assertThat(none.intersect(barBarBaz)).isEqualTo(none)
        Truth.assertThat(all.intersect(none)).isEqualTo(none)
        Truth.assertThat(all.intersect(all)).isEqualTo(all)
        Truth.assertThat(all.intersect(foo)).isEqualTo(foo)
        Truth.assertThat(all.intersect(bar)).isEqualTo(bar)
        Truth.assertThat(all.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(all.intersect(anyBarBaz)).isEqualTo(anyBarBaz)
        Truth.assertThat(all.intersect(fooBarAny)).isEqualTo(fooBarAny)
        Truth.assertThat(all.intersect(fooAnyAny)).isEqualTo(foo)
        Truth.assertThat(all.intersect(anyAnyBaz)).isEqualTo(anyAnyBaz)
        Truth.assertThat(all.intersect(barBarBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(foo.intersect(none)).isEqualTo(none)
        Truth.assertThat(foo.intersect(all)).isEqualTo(foo)
        Truth.assertThat(foo.intersect(foo)).isEqualTo(foo)
        Truth.assertThat(foo.intersect(bar)).isEqualTo(none)
        Truth.assertThat(foo.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(foo.intersect(anyBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(foo.intersect(fooBarAny)).isEqualTo(fooBarAny)
        Truth.assertThat(foo.intersect(fooAnyAny)).isEqualTo(foo)
        Truth.assertThat(foo.intersect(anyAnyBaz)).isEqualTo(fooAnyBaz)
        Truth.assertThat(foo.intersect(barBarBaz)).isEqualTo(none)
        Truth.assertThat(bar.intersect(none)).isEqualTo(none)
        Truth.assertThat(bar.intersect(all)).isEqualTo(bar)
        Truth.assertThat(bar.intersect(foo)).isEqualTo(none)
        Truth.assertThat(bar.intersect(bar)).isEqualTo(bar)
        Truth.assertThat(bar.intersect(fooBarBaz)).isEqualTo(none)
        Truth.assertThat(bar.intersect(anyBarBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(bar.intersect(fooBarAny)).isEqualTo(none)
        Truth.assertThat(bar.intersect(fooAnyAny)).isEqualTo(none)
        Truth.assertThat(bar.intersect(anyAnyBaz)).isEqualTo(barAnyBaz)
        Truth.assertThat(bar.intersect(barBarBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(fooBarBaz.intersect(none)).isEqualTo(none)
        Truth.assertThat(fooBarBaz.intersect(all)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(foo)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(bar)).isEqualTo(none)
        Truth.assertThat(fooBarBaz.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(anyBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(fooBarAny)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(fooAnyAny)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(anyAnyBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarBaz.intersect(barBarBaz)).isEqualTo(none)
        Truth.assertThat(anyBarBaz.intersect(none)).isEqualTo(none)
        Truth.assertThat(anyBarBaz.intersect(all)).isEqualTo(anyBarBaz)
        Truth.assertThat(anyBarBaz.intersect(foo)).isEqualTo(fooBarBaz)
        Truth.assertThat(anyBarBaz.intersect(bar)).isEqualTo(barBarBaz)
        Truth.assertThat(anyBarBaz.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(anyBarBaz.intersect(anyBarBaz)).isEqualTo(anyBarBaz)
        Truth.assertThat(anyBarBaz.intersect(fooBarAny)).isEqualTo(fooBarBaz)
        Truth.assertThat(anyBarBaz.intersect(fooAnyAny)).isEqualTo(fooBarBaz)
        Truth.assertThat(anyBarBaz.intersect(anyAnyBaz)).isEqualTo(anyBarBaz)
        Truth.assertThat(anyBarBaz.intersect(barBarBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(fooBarAny.intersect(none)).isEqualTo(none)
        Truth.assertThat(fooBarAny.intersect(all)).isEqualTo(fooBarAny)
        Truth.assertThat(fooBarAny.intersect(foo)).isEqualTo(fooBarAny)
        Truth.assertThat(fooBarAny.intersect(bar)).isEqualTo(none)
        Truth.assertThat(fooBarAny.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarAny.intersect(anyBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarAny.intersect(fooBarAny)).isEqualTo(fooBarAny)
        Truth.assertThat(fooBarAny.intersect(fooAnyAny)).isEqualTo(fooBarAny)
        Truth.assertThat(fooBarAny.intersect(anyAnyBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooBarAny.intersect(barBarBaz)).isEqualTo(none)
        Truth.assertThat(fooAnyAny.intersect(none)).isEqualTo(none)
        Truth.assertThat(fooAnyAny.intersect(all)).isEqualTo(foo)
        Truth.assertThat(fooAnyAny.intersect(foo)).isEqualTo(foo)
        Truth.assertThat(fooAnyAny.intersect(bar)).isEqualTo(none)
        Truth.assertThat(fooAnyAny.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooAnyAny.intersect(anyBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(fooAnyAny.intersect(fooBarAny)).isEqualTo(fooBarAny)
        Truth.assertThat(fooAnyAny.intersect(fooAnyAny)).isEqualTo(foo)
        Truth.assertThat(fooAnyAny.intersect(anyAnyBaz)).isEqualTo(fooAnyBaz)
        Truth.assertThat(fooAnyAny.intersect(barBarBaz)).isEqualTo(none)
        Truth.assertThat(anyAnyBaz.intersect(none)).isEqualTo(none)
        Truth.assertThat(anyAnyBaz.intersect(all)).isEqualTo(anyAnyBaz)
        Truth.assertThat(anyAnyBaz.intersect(foo)).isEqualTo(fooAnyBaz)
        Truth.assertThat(anyAnyBaz.intersect(bar)).isEqualTo(barAnyBaz)
        Truth.assertThat(anyAnyBaz.intersect(fooBarBaz)).isEqualTo(fooBarBaz)
        Truth.assertThat(anyAnyBaz.intersect(anyBarBaz)).isEqualTo(anyBarBaz)
        Truth.assertThat(anyAnyBaz.intersect(fooBarAny)).isEqualTo(fooBarBaz)
        Truth.assertThat(anyAnyBaz.intersect(fooAnyAny)).isEqualTo(fooAnyBaz)
        Truth.assertThat(anyAnyBaz.intersect(anyAnyBaz)).isEqualTo(anyAnyBaz)
        Truth.assertThat(anyAnyBaz.intersect(barBarBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(barBarBaz.intersect(none)).isEqualTo(none)
        Truth.assertThat(barBarBaz.intersect(all)).isEqualTo(barBarBaz)
        Truth.assertThat(barBarBaz.intersect(foo)).isEqualTo(none)
        Truth.assertThat(barBarBaz.intersect(bar)).isEqualTo(barBarBaz)
        Truth.assertThat(barBarBaz.intersect(fooBarBaz)).isEqualTo(none)
        Truth.assertThat(barBarBaz.intersect(anyBarBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(barBarBaz.intersect(fooBarAny)).isEqualTo(none)
        Truth.assertThat(barBarBaz.intersect(fooAnyAny)).isEqualTo(none)
        Truth.assertThat(barBarBaz.intersect(anyAnyBaz)).isEqualTo(barBarBaz)
        Truth.assertThat(barBarBaz.intersect(barBarBaz)).isEqualTo(barBarBaz)
    }

    @Test
    fun testContainsAll() {
        assertTrue(none.contains(none))
        assertFalse(none.contains(all))
        assertFalse(none.contains(foo))
        assertFalse(none.contains(bar))
        assertFalse(none.contains(fooBarBaz))
        assertFalse(none.contains(anyBarBaz))
        assertFalse(none.contains(fooBarAny))
        assertFalse(none.contains(fooAnyAny))
        assertFalse(none.contains(anyAnyBaz))
        assertFalse(none.contains(barBarBaz))
        assertTrue(all.contains(none))
        assertTrue(all.contains(all))
        assertTrue(all.contains(foo))
        assertTrue(all.contains(bar))
        assertTrue(all.contains(fooBarBaz))
        assertTrue(all.contains(anyBarBaz))
        assertTrue(all.contains(fooBarAny))
        assertTrue(all.contains(fooAnyAny))
        assertTrue(all.contains(anyAnyBaz))
        assertTrue(all.contains(barBarBaz))
        assertTrue(foo.contains(none))
        assertFalse(foo.contains(all))
        assertTrue(foo.contains(foo))
        assertFalse(foo.contains(bar))
        assertTrue(foo.contains(fooBarBaz))
        assertFalse(foo.contains(anyBarBaz))
        assertTrue(foo.contains(fooBarAny))
        assertTrue(foo.contains(fooAnyAny))
        assertFalse(foo.contains(anyAnyBaz))
        assertFalse(foo.contains(barBarBaz))
        assertTrue(bar.contains(none))
        assertFalse(bar.contains(all))
        assertFalse(bar.contains(foo))
        assertTrue(bar.contains(bar))
        assertFalse(bar.contains(fooBarBaz))
        assertFalse(bar.contains(anyBarBaz))
        assertFalse(bar.contains(fooBarAny))
        assertFalse(bar.contains(fooAnyAny))
        assertFalse(bar.contains(anyAnyBaz))
        assertTrue(bar.contains(barBarBaz))
        assertTrue(fooBarBaz.contains(none))
        assertFalse(fooBarBaz.contains(all))
        assertFalse(fooBarBaz.contains(foo))
        assertFalse(fooBarBaz.contains(bar))
        assertTrue(fooBarBaz.contains(fooBarBaz))
        assertFalse(fooBarBaz.contains(anyBarBaz))
        assertFalse(fooBarBaz.contains(fooBarAny))
        assertFalse(fooBarBaz.contains(fooAnyAny))
        assertFalse(fooBarBaz.contains(anyAnyBaz))
        assertFalse(fooBarBaz.contains(barBarBaz))
        assertTrue(anyBarBaz.contains(none))
        assertFalse(anyBarBaz.contains(all))
        assertFalse(anyBarBaz.contains(foo))
        assertFalse(anyBarBaz.contains(bar))
        assertTrue(anyBarBaz.contains(fooBarBaz))
        assertTrue(anyBarBaz.contains(anyBarBaz))
        assertFalse(anyBarBaz.contains(fooBarAny))
        assertFalse(anyBarBaz.contains(fooAnyAny))
        assertFalse(anyBarBaz.contains(anyAnyBaz))
        assertTrue(anyBarBaz.contains(barBarBaz))
        assertTrue(fooBarAny.contains(none))
        assertFalse(fooBarAny.contains(all))
        assertFalse(fooBarAny.contains(foo))
        assertFalse(fooBarAny.contains(bar))
        assertTrue(fooBarAny.contains(fooBarBaz))
        assertFalse(fooBarAny.contains(anyBarBaz))
        assertTrue(fooBarAny.contains(fooBarAny))
        assertFalse(fooBarAny.contains(fooAnyAny))
        assertFalse(fooBarAny.contains(anyAnyBaz))
        assertFalse(fooBarAny.contains(barBarBaz))
        assertTrue(fooAnyAny.contains(none))
        assertFalse(fooAnyAny.contains(all))
        assertTrue(fooAnyAny.contains(foo))
        assertFalse(fooAnyAny.contains(bar))
        assertTrue(fooAnyAny.contains(fooBarBaz))
        assertFalse(fooAnyAny.contains(anyBarBaz))
        assertTrue(fooAnyAny.contains(fooBarAny))
        assertTrue(fooAnyAny.contains(fooAnyAny))
        assertFalse(fooAnyAny.contains(anyAnyBaz))
        assertFalse(fooAnyAny.contains(barBarBaz))
        assertTrue(anyAnyBaz.contains(none))
        assertFalse(anyAnyBaz.contains(all))
        assertFalse(anyAnyBaz.contains(foo))
        assertFalse(anyAnyBaz.contains(bar))
        assertTrue(anyAnyBaz.contains(fooBarBaz))
        assertTrue(anyAnyBaz.contains(anyBarBaz))
        assertFalse(anyAnyBaz.contains(fooBarAny))
        assertFalse(anyAnyBaz.contains(fooAnyAny))
        assertTrue(anyAnyBaz.contains(anyAnyBaz))
        assertTrue(anyAnyBaz.contains(barBarBaz))
        assertTrue(barBarBaz.contains(none))
        assertFalse(barBarBaz.contains(all))
        assertFalse(barBarBaz.contains(foo))
        assertFalse(barBarBaz.contains(bar))
        assertFalse(barBarBaz.contains(fooBarBaz))
        assertFalse(barBarBaz.contains(anyBarBaz))
        assertFalse(barBarBaz.contains(fooBarAny))
        assertFalse(barBarBaz.contains(fooAnyAny))
        assertFalse(barBarBaz.contains(anyAnyBaz))
        assertTrue(barBarBaz.contains(barBarBaz))
    }

    @Test
    fun testIntersects() {
        val allPaths = listOf(
                none,
                all,
                foo,
                bar,
                fooBarBaz,
                anyBarBaz,
                fooBarAny,
                fooAnyAny,
                anyAnyBaz,
                barBarBaz,
                fooAnyBaz,
                barAnyBaz)

        assertThat(foo.intersects(anyBarBaz)).isTrue()
        assertThat(foo.intersect(anyBarBaz)).isEqualTo(fooBarBaz)

        for (path1 in allPaths) {
            for (path2 in allPaths) {
                val result = path1.intersect(path2).matchesAnything
                assertWithMessage("$path1.intersects($path2) == $result").that(path1.intersects(
                        path2)).isEqualTo(result)
            }
        }
    }
}
